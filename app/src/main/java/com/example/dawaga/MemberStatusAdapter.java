
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

    private double destinationLat;
    private double destinationLng;

    public MemberStatusAdapter(double destinationLat, double destinationLng) {
        this.destinationLat = destinationLat;
        this.destinationLng = destinationLng;
    }

    // Firestore에서 받은 멤버 정보 갱신
    public void updateMembers(List<Map<String, Object>> members) {
        memberList.clear();
        memberList.addAll(members);

        // 도착한 사람을 먼저, 도착 시간순으로 정렬
        Collections.sort(memberList, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> a, Map<String, Object> b) {
                boolean arrivedA = "ARRIVED".equals(a.get("status"));
                boolean arrivedB = "ARRIVED".equals(b.get("status"));

                if (arrivedA && arrivedB) {
                    long timeA = getArrivedAt(a);
                    long timeB = getArrivedAt(b);
                    return Long.compare(timeA, timeB);
                }

                if (arrivedA) return -1;
                if (arrivedB) return 1;

                return 0;
            }
        });

        notifyDataSetChanged();
    }

    private long getArrivedAt(Map<String, Object> member) {
        Object value = member.get("arrivedAt");

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        return Long.MAX_VALUE;
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_member_status, parent, false);

        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        Map<String, Object> member = memberList.get(position);

        String nickname = String.valueOf(member.get("nickname"));
        String status = String.valueOf(member.get("status"));

        holder.tvNickname.setText(nickname);

        if ("ARRIVED".equals(status)) {
            holder.tvRank.setText((position + 1) + "등");
            holder.tvDistance.setText("약속 장소 도착!");
            holder.tvArrivalStatus.setText("도착 완료");
            holder.tvArrivalStatus.setTextColor(0xFF16A34A);

        } else {
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

            int distance = (int) results[0];

            holder.tvRank.setText("-");
            holder.tvDistance.setText(distance + "m");

            // 시속 약 4.8km 기준, 분당 약 80m 이동으로 예상
            int estimatedMinutes = Math.max(1, distance / 80);

            holder.tvArrivalStatus.setText("약 " + estimatedMinutes + "분");
            holder.tvArrivalStatus.setTextColor(0xFF3366CC);
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
