package com.google.android.apps.location.gps.gnsslogger;

import android.location.GnssClock;

/**
 * Created by 张楷时 on 2017/4/13.
 * 主要内容：定义关于GPS时的相关特征和变量
 */

public class GpsTime {
	public final static int DAYSEC=24*3600;
	public final static int HOURSEC=3600;
	public final static int MINSEC=60;
	public final static int[] monthDays={31,28,31,30,31,30,31,31,30,31,30,31};

	private int year;
	private int month;
	private int date;
	private int hour;
	private int min;
	private double sec;

	public int getYear() {
		return year;
	}

	public void setYear(int year) {
		this.year = year;
	}

	public int getMonth() {
		return month;
	}

	public void setMonth(int month) {
		this.month = month;
	}

	public int getDate() {
		return date;
	}

	public void setDate(int date) {
		this.date = date;
	}

	public int getHour() {
		return hour;
	}

	public void setHour(int hour) {
		this.hour = hour;
	}

	public int getMin() {
		return min;
	}

	public void setMin(int min) {
		this.min = min;
	}

	public double getSec() {
		return sec;
	}

	public void setSec(double sec) {
		this.sec = sec;
	}

	public GpsTime(GnssClock mGnssClock) {
		double FullBias=mGnssClock.getFullBiasNanos();
		double Bias=mGnssClock.getBiasNanos();
		double Time=mGnssClock.getTimeNanos();
		double fctSec=Time-FullBias-Bias;
		year=1980;
		month=1;
		fctSec=fctSec/1000000000;
		date=(int)Math.floor(fctSec/DAYSEC)+6;
		int leap=1;
		while(date>(leap+365))
		{
			date=date-(leap+365);
			year=year+1;
			if(year%4!=0)  leap=0;
			else leap=1;
		}
		if(year%4==0) monthDays[1]=29;
		for(int i=0;i<12;i++)
		{
			if(date>monthDays[i]){
				date=date-monthDays[i];
				month+=1;
			}
			else break;
		}
		sec=fctSec%DAYSEC;
		hour=(int) sec/HOURSEC;
		sec=sec%HOURSEC;
		min=(int)sec/MINSEC;
		sec=sec%MINSEC;
		sec=(double)Math.round(sec);
		if(sec>=60){
			sec=sec-60;
			min=min+1;
		}
	}
}
