
package com.example.dawaga;

import android.location.Location;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class MemberStatusAdapter extends RecyclerView.Adapter<MemberStatusAdapter.MemberViewHolder> {

    private final List<Map<String, Object>> memberList = new ArrayList<>();

    private final double destinationLat;
    private final double destinationLng;

    // 현재 로그인한 사용자 ID
    private final String currentUserId;

    public MemberStatusAdapter(
            double destinationLat,
            double destinationLng,
            String currentUserId
    ) {
        this.destinationLat = destinationLat;
        this.destinationLng = destinationLng;
        this.currentUserId = currentUserId;
    }

    // Firestore에서 받은 멤버 정보 갱신
    public void updateMembers(List<Map<String, Object>> members) {

        memberList.clear();
        memberList.addAll(members);

        // 도착한 사람 먼저, 미도착자는 가까운 순서대로 정렬
        Collections.sort(memberList, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> a, Map<String, Object> b) {

                boolean arrivedA = "ARRIVED".equals(a.get("status"));
                boolean arrivedB = "ARRIVED".equals(b.get("status"));

                if (arrivedA && arrivedB) {
                    return Long.compare(getArrivedAt(a), getArrivedAt(b));
                }

                if (arrivedA) return -1;
                if (arrivedB) return 1;

                return Double.compare(getDistance(a), getDistance(b));
            }
        });

        notifyDataSetChanged();
    }

    // 도착 시간 가져오기
    private long getArrivedAt(Map<String, Object> member) {

        Object value = member.get("arrivedAt");

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        return Long.MAX_VALUE;
    }

    // 약속 장소까지 거리 계산
    private double getDistance(Map<String, Object> member) {

        double latitude = getDouble(member.get("latitude"));
        double longitude = getDouble(member.get("longitude"));

        float[] results = new float[1];

        Location.distanceBetween(
                latitude,
                longitude,
                destinationLat,
                destinationLng,
                results
        );

        return results[0];
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_member_status, parent, false);

        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull MemberViewHolder holder,
            int position
    ) {

        Map<String, Object> member = memberList.get(position);

        String nickname = String.valueOf(member.get("nickname"));
        String status = String.valueOf(member.get("status"));

        // 현재 친구의 ID 가져오기
        String memberUserId = String.valueOf(member.get("userId"));

        // 내 ID와 친구 ID 비교
        boolean isMe = currentUserId != null
                && currentUserId.equals(memberUserId);

        // 본인이라면 닉네임 뒤에 '· 나' 표시
        if (isMe) {
            holder.tvNickname.setText(nickname + " · 나");
        } else {
            holder.tvNickname.setText(nickname);
        }

        // 순위 표시
        holder.tvRank.setText((position + 1) + ".");

        if ("ARRIVED".equals(status)) {

            // 도착한 친구
            holder.tvDistance.setText("");
            holder.tvArrivalStatus.setText("도착");

            holder.tvArrivalStatus.setTextColor(0xFF222222);
            holder.tvArrivalStatus.setTypeface(
                    null,
                    android.graphics.Typeface.BOLD
            );

        } else {

            // 아직 도착하지 않은 친구
            int distance = (int) getDistance(member);

            holder.tvDistance.setText(distance + "m");

            // 시속 약 4.8km 기준
            int estimatedMinutes = Math.max(1, distance / 80);

            holder.tvArrivalStatus.setText(estimatedMinutes + "분");

            holder.tvArrivalStatus.setTextColor(0xFF777777);
            holder.tvArrivalStatus.setTypeface(
                    null,
                    android.graphics.Typeface.NORMAL
            );
        }
    }

    // 숫자 데이터 변환
    private double getDouble(Object value) {

        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }

        return 0.0;
    }

    @Override
    public int getItemCount() {
        return memberList.size();
    }

    // ViewHolder
    static class MemberViewHolder extends RecyclerView.ViewHolder {

        TextView tvRank;
        TextView tvNickname;
        TextView tvDistance;
        TextView tvArrivalStatus;

        public MemberViewHolder(@NonNull View itemView) {
            super(itemView);

            tvRank = itemView.findViewById(R.id.tv_rank);
            tvNickname = itemView.findViewById(R.id.tv_nickname);
            tvDistance = itemView.findViewById(R.id.tv_distance);
            tvArrivalStatus = itemView.findViewById(R.id.tv_arrival_status);
        }
    }
}
