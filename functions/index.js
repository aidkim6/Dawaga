
const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

initializeApp();

const db = getFirestore();

/**
 * 도착 결과 생성
 */
function createFinalResults(members, meetTime) {
  return members.map((member) => {
    const result = { ...member };
    const arrivedAt = Number(member.arrivedAt);

    if (member.status === "ARRIVED" && Number.isFinite(arrivedAt)) {
      result.resultStatus =
        arrivedAt <= meetTime ? "ON_TIME" : "LATE";
    } else {
      result.resultStatus = "NO_SHOW";
    }

    return result;
  });
}

/**
 * 모든 참여자가 도착하면 자동 마감
 */
exports.autoCloseWhenAllArrived = onDocumentWritten(
  "rooms/{roomCode}/members/{userId}",
  async (event) => {
    const { roomCode } = event.params;

    const roomRef = db.collection("rooms").doc(roomCode);
    const membersRef = roomRef.collection("members");

    await db.runTransaction(async (transaction) => {
      // 모든 읽기를 쓰기보다 먼저 수행
      const roomSnapshot = await transaction.get(roomRef);

      if (!roomSnapshot.exists) return;

      const room = roomSnapshot.data();

      if (room.status !== "ACTIVE") return;

      const meetTime = Number(room.meetTime);
      const expirationTime = Number(room.expirationTime);

      if (!Number.isFinite(meetTime)) return;

      // 약속 시간 1시간이 지났다면 자동 마감하지 않음
      if (
        Number.isFinite(expirationTime) &&
        Date.now() >= expirationTime
      ) {
        return;
      }

      // 트랜잭션 안에서 최신 참여자 목록 확인
      const membersSnapshot = await transaction.get(membersRef);

      if (membersSnapshot.empty) return;

      const members = membersSnapshot.docs.map((doc) => ({
        ...doc.data(),
        userId: doc.id,
      }));

      const allArrived = members.every(
        (member) => member.status === "ARRIVED"
      );

      if (!allArrived) return;

      const now = Date.now();
      const finalResults = createFinalResults(members, meetTime);

      transaction.update(roomRef, {
        status: "CLOSED",
        closedBy: "SYSTEM_ALL_ARRIVED",
        closeReason: "ALL_ARRIVED",
        closedAt: now,
        finalResults,
        resultsSavedAt: now,
      });
    });

    console.log(`[${roomCode}] 전체 도착 자동 마감 처리 확인`);
  }
);

/**
 * 약속 시간 1시간 후 미마감 방 자동 만료
 * 1분마다 실행
 */
exports.expireRooms = onSchedule(
  {
    schedule: "every 1 minutes",
    timeZone: "Asia/Seoul",
  },
  async () => {
    const now = Date.now();

    // ACTIVE 방만 조회하고 만료 시각은 코드에서 확인
    const activeRooms = await db
      .collection("rooms")
      .where("status", "==", "ACTIVE")
      .get();

    for (const roomDoc of activeRooms.docs) {
      const roomRef = roomDoc.ref;

      await db.runTransaction(async (transaction) => {
        const roomSnapshot = await transaction.get(roomRef);

        if (!roomSnapshot.exists) return;

        const room = roomSnapshot.data();
        const expirationTime = Number(room.expirationTime);

        if (
          room.status !== "ACTIVE" ||
          !Number.isFinite(expirationTime) ||
          expirationTime > now
        ) {
          return;
        }

        transaction.update(roomRef, {
          status: "EXPIRED",
          expiredAt: now,
          expiredReason: "ONE_HOUR_AFTER_MEET_TIME",
        });
      });

      console.log(`[${roomDoc.id}] 자동 만료 확인`);
    }
  }
);
