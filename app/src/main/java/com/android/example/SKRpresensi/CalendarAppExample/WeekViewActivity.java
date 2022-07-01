package com.android.example.SKRpresensi.CalendarAppExample;

import static codewithcal.au.calendarappexample.CalendarUtils.daysInWeekArray;
import static codewithcal.au.calendarappexample.CalendarUtils.monthYearFromDate;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.time.LocalDate;
import java.util.ArrayList;

public class WeekViewActivity extends AppCompatActivity implements CalendarAdapter.OnItemListener
{
    private TextView monthYearText;
    private RecyclerView calendarRecyclerView;
    private ListView eventListView;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_week_view);
        initWidgets();
        setWeekView();
    }

    private void initWidgets()
    {
        calendarRecyclerView = findViewById(R.id.calendarRecyclerView);
        monthYearText = findViewById(R.id.monthYearTV);
        eventListView = findViewById(R.id.eventListView);
    }

    private void setWeekView()
    {
        monthYearText.setText(monthYearFromDate(codewithcal.au.calendarappexample.CalendarUtils.selectedDate));
        ArrayList<LocalDate> days = daysInWeekArray(codewithcal.au.calendarappexample.CalendarUtils.selectedDate);

        CalendarAdapter calendarAdapter = new CalendarAdapter(days, this);
        RecyclerView.LayoutManager layoutManager = new GridLayoutManager(getApplicationContext(), 7);
        calendarRecyclerView.setLayoutManager(layoutManager);
        calendarRecyclerView.setAdapter(calendarAdapter);
        setEventAdpater();
    }


    public void previousWeekAction(View view)
    {
        codewithcal.au.calendarappexample.CalendarUtils.selectedDate = codewithcal.au.calendarappexample.CalendarUtils.selectedDate.minusWeeks(1);
        setWeekView();
    }

    public void nextWeekAction(View view)
    {
        codewithcal.au.calendarappexample.CalendarUtils.selectedDate = codewithcal.au.calendarappexample.CalendarUtils.selectedDate.plusWeeks(1);
        setWeekView();
    }

    @Override
    public void onItemClick(int position, LocalDate date)
    {
        codewithcal.au.calendarappexample.CalendarUtils.selectedDate = date;
        setWeekView();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        setEventAdpater();
    }

    private void setEventAdpater()
    {
        ArrayList<codewithcal.au.calendarappexample.Event> dailyEvents = codewithcal.au.calendarappexample.Event.eventsForDate(codewithcal.au.calendarappexample.CalendarUtils.selectedDate);
        codewithcal.au.calendarappexample.EventAdapter eventAdapter = new codewithcal.au.calendarappexample.EventAdapter(getApplicationContext(), dailyEvents);
        eventListView.setAdapter(eventAdapter);
    }

    public void newEventAction(View view)
    {
        startActivity(new Intent(this, codewithcal.au.calendarappexample.EventEditActivity.class));
    }

    public void dailyAction(View view)
    {
        startActivity(new Intent(this, codewithcal.au.calendarappexample.DailyCalendarActivity.class));
    }
}