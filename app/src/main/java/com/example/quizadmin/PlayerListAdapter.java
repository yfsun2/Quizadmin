package com.example.quizadmin;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import java.util.List;

public class PlayerListAdapter extends ArrayAdapter<String> {
    private Context context;
    private List<String> players;

    public PlayerListAdapter(Context context, List<String> players) {
        super(context, 0, players);
        this.context = context;
        this.players = players;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.player_item, parent, false);
        }

        TextView tvPlayerName = convertView.findViewById(R.id.tvPlayerName);
        String playerName = players.get(position);
        tvPlayerName.setText(playerName);

        return convertView;
    }
}
