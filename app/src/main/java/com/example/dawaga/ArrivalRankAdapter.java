
package com.example.dawaga;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ArrivalRankAdapter
        extends RecyclerView.Adapter<ArrivalRankAdapter.RankViewHolder> {

    private final List<Map<String, Object>> members = new ArrayList<>();

    // Firestore 멤버 목록을 받아 도착한 사람만 추리고 정렬
    public void updateMembers(List<Map<String, Object>> newMembers) {
        members.clear();

        for (Map<String, Object> member : newMembers) {
            if ("ARRIVED".equals(member.get("status"))) {
                members.add(member);
            }
        }

        // 도착 시각이 빠른 순서대로 정렬
        Collections.sort(members, (a, b) ->
                Long.compare(getArrivedAt(a), getArrivedAt(b))
        );

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
    public RankViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_arrival_rank, parent, false);

        return new RankViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull RankViewHolder holder,
            int position) {

        Map<String, Object> member = members.get(position);

        Object nicknameValue = member.get("nickname");
        String nickname = nicknameValue == null
                ? "이름 없음"
                : nicknameValue.toString();

        holder.tvRank.setText(String.valueOf(position + 1));
        holder.tvName.setText(nickname);

        long arrivedAt = getArrivedAt(member);

        if (arrivedAt != Long.MAX_VALUE) {
            SimpleDateFormat format =
                    new SimpleDateFormat("HH:mm", Locale.KOREA);

            holder.tvTime.setText(
                    format.format(new Date(arrivedAt))
            );
        } else {
            holder.tvTime.setText("--:--");
        }
    }

    @Override
    public int getItemCount() {
        return members.size();
    }

    static class RankViewHolder extends RecyclerView.ViewHolder {

        TextView tvRank;
        TextView tvName;
        TextView tvTime;

        RankViewHolder(@NonNull View itemView) {
            super(itemView);

            tvRank = itemView.findViewById(R.id.tv_rank);
            tvName = itemView.findViewById(R.id.tv_rank_name);
            tvTime = itemView.findViewById(R.id.tv_arrival_time);
        }
    }
}
