package com.example.instantsave;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.text.format.DateFormat;

import com.example.instantsave.historyRecyclerView.HistoryAdapter;
import com.example.instantsave.historyRecyclerView.HistoryObject;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {
    private String clientOrAmbulance, userId;
    private RecyclerView mHistoryRecyclerView;
    private RecyclerView.Adapter mHistoryAdapter;
    private RecyclerView.LayoutManager mHistoryLayoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        mHistoryRecyclerView = (RecyclerView) findViewById(R.id.historyRecyclerView);
        mHistoryRecyclerView.setNestedScrollingEnabled(false);
        mHistoryRecyclerView.setHasFixedSize(true);
        mHistoryLayoutManager = new LinearLayoutManager(HistoryActivity.this);
        mHistoryRecyclerView.setLayoutManager(mHistoryLayoutManager);
        mHistoryAdapter = new HistoryAdapter(getDataSetHistory(), HistoryActivity.this);
        mHistoryRecyclerView.setAdapter(mHistoryAdapter);

        clientOrAmbulance=getIntent().getExtras().getString("clientOrAmbulance");
        userId= FirebaseAuth.getInstance().getCurrentUser().getUid();
        getUserHistoryIds();

    }

    private void getUserHistoryIds() {
        DatabaseReference userHistoryDatabase= FirebaseDatabase.getInstance().getReference().child("Users").child(clientOrAmbulance).child(userId).child("history");
        userHistoryDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()){
                    for (DataSnapshot history : snapshot.getChildren()){
                        FetchHelpInformation(history.getKey());
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void FetchHelpInformation(String helpKey) {
        DatabaseReference historyDatabase= FirebaseDatabase.getInstance().getReference().child("history").child(helpKey);
        historyDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()){
                    String helpId=snapshot.getKey();
                    Long timestamp=0L;
                    for (DataSnapshot child : snapshot.getChildren()){
                        if (child.getKey().equals("timestamp")){
                            timestamp=Long.valueOf(child.getValue().toString());
                        }
                    }
                    HistoryObject obj=new HistoryObject(helpId, getData(timestamp));
                    resultsHistory.add(obj);
                    mHistoryAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private String getData(Long timestamp) {
        Calendar cal=Calendar.getInstance(Locale.getDefault());
        cal.setTimeInMillis(timestamp*1000);
        String date= DateFormat.format("dd-MM-yyyy hh:mm", cal).toString();
        return date;
    }

    private ArrayList resultsHistory=new ArrayList<HistoryObject>();
    private ArrayList<HistoryObject> getDataSetHistory() {
        return resultsHistory;
    }
}