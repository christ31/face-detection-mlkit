package com.android.example.SKRpresensi.CalendarAppExample;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.time.LocalTime;

public class EventEditActivity extends AppCompatActivity
{
    private EditText eventNameET;
    private TextView eventDateTV, eventTimeTV;

    private LocalTime time;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_edit);
        initWidgets();
        time = LocalTime.now();
        eventDateTV.setText("Date: " + codewithcal.au.calendarappexample.CalendarUtils.formattedDate(codewithcal.au.calendarappexample.CalendarUtils.selectedDate));
        eventTimeTV.setText("Time: " + codewithcal.au.calendarappexample.CalendarUtils.formattedTime(time));
    }

    private void initWidgets()
    {
        eventNameET = findViewById(R.id.eventNameET);
        eventDateTV = findViewById(R.id.eventDateTV);
        eventTimeTV = findViewById(R.id.eventTimeTV);
    }

    public void saveEventAction(View view)
    {
        String eventName = eventNameET.getText().toString();
        codewithcal.au.calendarappexample.Event newEvent = new codewithcal.au.calendarappexample.Event(eventName, codewithcal.au.calendarappexample.CalendarUtils.selectedDate, time);
        codewithcal.au.calendarappexample.Event.eventsList.add(newEvent);
        finish();
    }
}