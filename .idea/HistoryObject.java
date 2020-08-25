package com.example.instantsave.historyRecyclerView;

public class HistoryObject {
    private String helpId;
    private String time;

    public HistoryObject(String helpId, String time){
        this.helpId=helpId;
        this.time=time;
    }
    public String getHelpId(){return helpId;}
    public void setHelpId(String helpId){ this.helpId=helpId; }
    public String getTime(){return time;}
    public void setTime(String time){ this.time=time; }
}
