package com.android.example.SKRpresensi.CalendarAppExample;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.time.LocalDate;
import java.util.ArrayList;

class CalendarAdapter extends RecyclerView.Adapter<codewithcal.au.calendarappexample.CalendarViewHolder>
{
    private final ArrayList<LocalDate> days;
    private final OnItemListener onItemListener;

    public CalendarAdapter(ArrayList<LocalDate> days, OnItemListener onItemListener)
    {
        this.days = days;
        this.onItemListener = onItemListener;
    }

    @NonNull
    @Override
    public codewithcal.au.calendarappexample.CalendarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.calendar_cell, parent, false);
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if(days.size() > 15) //month view
            layoutParams.height = (int) (parent.getHeight() * 0.166666666);
        else // week view
            layoutParams.height = (int) parent.getHeight();

        return new codewithcal.au.calendarappexample.CalendarViewHolder(view, onItemListener, days);
    }

    @Override
    public void onBindViewHolder(@NonNull codewithcal.au.calendarappexample.CalendarViewHolder holder, int position)
    {
        final LocalDate date = days.get(position);

        holder.dayOfMonth.setText(String.valueOf(date.getDayOfMonth()));

        if(date.equals(codewithcal.au.calendarappexample.CalendarUtils.selectedDate))
            holder.parentView.setBackgroundColor(Color.LTGRAY);

        if(date.getMonth().equals(codewithcal.au.calendarappexample.CalendarUtils.selectedDate.getMonth()))
            holder.dayOfMonth.setTextColor(Color.BLACK);
        else
            holder.dayOfMonth.setTextColor(Color.LTGRAY);
    }

    @Override
    public int getItemCount()
    {
        return days.size();
    }

    public interface  OnItemListener
    {
        void onItemClick(int position, LocalDate date);
    }
}
