
package com.example.dawaga;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.dawaga.location.LocationRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class MemberStatusAdapter
        extends RecyclerView.Adapter<MemberStatusAdapter.MemberViewHolder> {

    private final List<Map<String, Object>> memberList =
            new ArrayList<>();

    private final double destinationLat;
    private final double destinationLng;
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

    // Firestore 데이터 갱신
    public void updateMembers(
            List<Map<String, Object>> members
    ) {
        memberList.clear();

        if (members != null) {
            memberList.addAll(members);
        }

        // 도착한 사람 먼저, 나머지는 가까운 순
        Collections.sort(
                memberList,
                new Comparator<Map<String, Object>>() {

                    @Override
                    public int compare(
                            Map<String, Object> a,
                            Map<String, Object> b
                    ) {

                        boolean arrivedA =
                                "ARRIVED".equals(a.get("status"));

                        boolean arrivedB =
                                "ARRIVED".equals(b.get("status"));

                        if (arrivedA && arrivedB) {
                            return Long.compare(
                                    getArrivedAt(a),
                                    getArrivedAt(b)
                            );
                        }

                        if (arrivedA) return -1;
                        if (arrivedB) return 1;

                        return Double.compare(
                                getDistance(a),
                                getDistance(b)
                        );
                    }
                }
        );

        notifyDataSetChanged();
    }

    // 도착 시간
    private long getArrivedAt(
            Map<String, Object> member
    ) {

        Object value = member.get("arrivedAt");

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        return Long.MAX_VALUE;
    }

    // 약속 장소까지 거리
    private double getDistance(
            Map<String, Object> member
    ) {

        double latitude =
                getDouble(member.get("latitude"));

        double longitude =
                getDouble(member.get("longitude"));

        // GPS 위치를 아직 받지 못한 경우
        if (latitude == 0.0 && longitude == 0.0) {
            return Double.MAX_VALUE;
        }

        return LocationRules.distanceMeters(
                latitude,
                longitude,
                destinationLat,
                destinationLng
        );
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        View view = LayoutInflater.from(
                parent.getContext()
        ).inflate(
                R.layout.item_member_status,
                parent,
                false
        );

        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull MemberViewHolder holder,
            int position
    ) {

        Map<String, Object> member =
                memberList.get(position);

        String nickname =
                String.valueOf(member.get("nickname"));

        String status =
                String.valueOf(member.get("status"));

        String memberUserId =
                String.valueOf(member.get("userId"));

        // 내 이름 표시
        boolean isMe =
                currentUserId != null
                        && currentUserId.equals(memberUserId);

        if (isMe) {
            holder.tvNickname.setText(
                    nickname + " · 나"
            );
        } else {
            holder.tvNickname.setText(nickname);
        }

        // 순위 표시
        holder.tvRank.setText(
                (position + 1) + "."
        );

        if ("ARRIVED".equals(status)) {

            // 도착 완료
            holder.tvDistance.setText("");

            holder.tvArrivalStatus.setText("도착");

            holder.tvArrivalStatus.setTextColor(
                    0xFF3979D6
            );

            holder.tvArrivalStatus.setTypeface(
                    null,
                    Typeface.BOLD
            );

        } else {

            // 미도착
            double distance = getDistance(member);

            if (distance == Double.MAX_VALUE) {

                holder.tvDistance.setText("위치 확인 중");

                holder.tvArrivalStatus.setText("");

            } else {

                // 거리 표시
                holder.tvDistance.setText(
                        LocationRules.formatDistance(distance)
                );

                // 공통 예상 도착 시간 계산
                int estimatedMinutes =
                        LocationRules.estimateMinutes(distance);

                holder.tvArrivalStatus.setText(
                        estimatedMinutes + "분"
                );
            }

            holder.tvArrivalStatus.setTextColor(
                    0xFF777777
            );

            holder.tvArrivalStatus.setTypeface(
                    null,
                    Typeface.NORMAL
            );
        }
    }

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

    static class MemberViewHolder
            extends RecyclerView.ViewHolder {

        TextView tvRank;
        TextView tvNickname;
        TextView tvDistance;
        TextView tvArrivalStatus;

        public MemberViewHolder(
                @NonNull View itemView
        ) {

            super(itemView);

            tvRank = itemView.findViewById(
                    R.id.tv_rank
            );

            tvNickname = itemView.findViewById(
                    R.id.tv_nickname
            );

            tvDistance = itemView.findViewById(
                    R.id.tv_distance
            );

            tvArrivalStatus = itemView.findViewById(
                    R.id.tv_arrival_status
            );
        }
    }
}
