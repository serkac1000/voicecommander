package com.yourpackage.voicecontrol;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class CommandsAdapter extends BaseAdapter {
    private Context context;
    private LayoutInflater inflater;
    
    public CommandsAdapter(Context context) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
    }
    
    @Override
    public int getCount() {
        return 10;
    }
    
    @Override
    public Object getItem(int position) {
        return position;
    }
    
    @Override
    public long getItemId(int position) {
        return position;
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.list_item_command, parent, false);
            holder = new ViewHolder();
            holder.title = convertView.findViewById(R.id.command_title);
            holder.subtitle = convertView.findViewById(R.id.command_subtitle);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        
        if (position == 0) {
            holder.title.setText("Vocal command n°1");
            holder.subtitle.setText("Configure data to send");
            holder.subtitle.setVisibility(View.VISIBLE);
        } else {
            holder.title.setText("Vocal command n°" + (position + 1));
            holder.subtitle.setVisibility(View.GONE);
        }
        
        return convertView;
    }
    
    static class ViewHolder {
        TextView title;
        TextView subtitle;
    }
}