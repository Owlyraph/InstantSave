package com.example.instantsave.historyRecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.instantsave.R;

class HistoryViewHolders extends RecyclerView.ViewHolder implements View.OnClickListener {

    public TextView helpId;
    public TextView time;
    public HistoryViewHolders(@NonNull View itemView) {
        super(itemView);
        itemView.setOnClickListener(this);

        helpId=(TextView) itemView.findViewById(R.id.helpId);
        time=(TextView) itemView.findViewById(R.id.time);
    }

    @Override
    public void onClick(View v) {
        Intent intent=new Intent(v.getContext(), HistorySingleActivity.class);
        Bundle b=new Bundle();
        b.putString("helpId", helpId.getText().toString());
        intent.putExtras(b);
        v.getContext().startActivity(intent);

    }
}
