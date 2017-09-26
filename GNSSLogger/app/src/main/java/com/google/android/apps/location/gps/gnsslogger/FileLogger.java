/*
修改作者：张楷时 Cash
修改时间：2017.04
修改内容：重新编写GnssLogger软件的输出文件格式，使之符合RINEX文件要求
 */
package com.google.android.apps.location.gps.gnsslogger;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.google.android.apps.location.gps.gnsslogger.LoggerFragment.UIFragmentComponent;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * A GNSS logger to store information to a file.
 */
public class FileLogger implements GnssListener {

    //private LocationManager locationManager;
    //private Location location;
    private static final String TAG = "FileLogger";
    private static final String FILE_PREFIX = "ObsFile";
    private static final String ERROR_WRITING_FILE = "Problem writing to file.";
    //private static final String COMMENT_START = "# ";
    //private static final char RECORD_DELIMITER = ',';
    //private static final String VERSION_TAG = "Version: ";
    //private static final String FILE_VERSION = "1.4.0.0, Platform: N";
    public final static int MAXTOWUNCNS = 500;
    public final static int MAXPRRUNCMPS = 20;
    public final static int MINCN0DBHZ = 30;
    public final static int MAXGPSSVID = 32;
    private static final int MAX_FILES_STORED = 100;
    private static final int MINIMUM_USABLE_FILE_SIZE_BYTES = 1000;
    private final Context mContext;
    private final Object mFileLock = new Object();
    private BufferedWriter mFileWriter;
    private File mFile;
    private UIFragmentComponent mUiComponent;

    public synchronized UIFragmentComponent getUiComponent() {
        return mUiComponent;
    }

    public synchronized void setUiComponent(UIFragmentComponent value) {
        mUiComponent = value;
    }

    public FileLogger(Context context) {
        this.mContext = context;
    }

    /**
     * Start a new file logging process.
     */
    public void startNewLog() {
        synchronized (mFileLock) {

            //创建存储观测文件的文件夹（/ObsFile）
            File baseDirectory;
            String state = Environment.getExternalStorageState();
            if (Environment.MEDIA_MOUNTED.equals(state)) {
                baseDirectory = new File(Environment.getExternalStorageDirectory(), FILE_PREFIX);
                baseDirectory.mkdirs();
            } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                logError("Cannot write to external storage.");
                return;
            } else {
                logError("Cannot read external storage.");
                return;
            }

            //创建观测值文件和输入文件流
            SimpleDateFormat formatter = new SimpleDateFormat("yyy_MM_dd_HH_mm_ss");
            Date now = new Date();
            String fileName = String.format("%s_log_%s.obs", FILE_PREFIX, formatter.format(now));
            File currentFile = new File(baseDirectory, fileName);
            String currentFilePath = currentFile.getAbsolutePath();//获取文件路径
            BufferedWriter currentFileWriter;//创建文件输入流
            try {
                currentFileWriter = new BufferedWriter(new FileWriter(currentFile));
            } catch (IOException e) {
                logException("Could not open file: " + currentFilePath, e);
                return;
            }
            /*
            locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
            if (ActivityCompat.checkSelfPermission(this.mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this.mContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            double lat=location.getLatitude();
            double alt=location.getAltitude();
            double lng=location.getLongitude();
            Log.d(TAG,"Latitude: "+lat);
            Log.d(TAG,"Longtitude: "+lng);
            Log.d(TAG,"Altitude: "+alt);
            */

            // 输入Obs文件的文件头  initialize the header of the observation file
            try {
                Log.d(TAG,"Start Write File");
                currentFileWriter.write("     2.11           OBSERVATION DATA    G (GPS)             RINEX VERSION / TYPE\n");
                currentFileWriter.write("LoggerReader        CASHZHANG WHU       ");
                SimpleDateFormat formatter2 = new SimpleDateFormat("yyyyMMdd HHmmss");
                currentFileWriter.write(String.format(formatter2.format(now))+" UTC PGM / RUN BY / DATE\n");
                currentFileWriter.write("                                                            MARKER NAME\n");
                currentFileWriter.write("                                                            OBSERVER / AGENCY\n");
                currentFileWriter.write("                                                            REC # / TYPE / VERS\n");
                currentFileWriter.write("                                                            ANT # / TYPE\n");
                currentFileWriter.write(" -2267662.7966  5008768.0193  3221937.1078                  APPROX POSITION XYZ\n");
                currentFileWriter.write("        0.0000        0.0000        0.0000                  ANTENNA: DELTA H/E/N\n");
                currentFileWriter.write("     1     0                                                WAVELENGTH FACT L1/2\n");
                currentFileWriter.write("     3    C1    L1    S1                                    # / TYPES OF OBSERV\n");
                SimpleDateFormat formatter3 = new SimpleDateFormat("  yyyy    MM    dd    HH    mm   ss");
                currentFileWriter.write(String.format(formatter3.format(now))+".0000000     GPS         TIME OF FIRST OBS\n");
                currentFileWriter.write("                                                            END OF HEADER\n");
            } catch (IOException e) {
                logException("Count not initialize file: " + currentFilePath, e);
                return;
            }

            //确认mFileWriter文件流为空
            if (mFileWriter != null) {
                try {
                    mFileWriter.close();
                } catch (IOException e) {
                    logException("Unable to close all file streams.", e);
                    return;
                }
            }

            //输入观测值
            mFile = currentFile;
            mFileWriter = currentFileWriter;
            Toast.makeText(mContext, "File opened: " + currentFilePath, Toast.LENGTH_SHORT).show();

            // 确保存储的观测值文件不会填满硬盘 To make sure that files do not fill up the external storage:
            // - Remove all empty files
            FileFilter filter = new FileToDeleteFilter(mFile);
            for (File existingFile : baseDirectory.listFiles(filter)) {
                existingFile.delete();
            }
            // - Trim the number of files with data
            File[] existingFiles = baseDirectory.listFiles();
            int filesToDeleteCount = existingFiles.length - MAX_FILES_STORED;
            if (filesToDeleteCount > 0) {
                Arrays.sort(existingFiles);
                for (int i = 0; i < filesToDeleteCount; ++i) {
                    existingFiles[i].delete();
                }
            }
        }
    }

    /*
      观测值文件分享机制
      Send the current log via email or other options selected from a pop menu shown to the user. A
      new log is started when calling this function.
     */
    public void send() {
        if (mFile == null) {
            return;
        }

        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setType("*/*");
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "SensorLog");
        emailIntent.putExtra(Intent.EXTRA_TEXT, "");
        // attach the file
        emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(mFile));
        getUiComponent().startActivity(Intent.createChooser(emailIntent, "Send log.."));
        if (mFileWriter != null) {
            try {
                mFileWriter.close();
                mFileWriter = null;
            } catch (IOException e) {
                logException("Unable to close all file streams.", e);
                return;
            }
        }
    }

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}

    @Override
    public void onLocationChanged(Location location) {

       /* if (location.getProvider().equals(LocationManager.GPS_PROVIDER)) {
            synchronized (mFileLock) {
                if (mFileWriter == null) {
                    return;
                }
                String locationStream =
                        String.format(
                                Locale.US,
                                "Fix,%s,%f,%f,%f,%f,%f,%d",
                                location.getProvider(),
                                location.getLatitude(),
                                location.getLongitude(),
                                location.getAltitude(),
                                location.getSpeed(),
                                location.getAccuracy(),
                                location.getTime());
                try {
                    mFileWriter.write(locationStream);
                    mFileWriter.newLine();
                } catch (IOException e) {
                    logException(ERROR_WRITING_FILE, e);
                }
            }
        }
        */
    }

    @Override
    public void onLocationStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {
        synchronized (mFileLock) {
            if (mFileWriter == null) {
                return;
            }
            GnssClock gnssClock = event.getClock();
            Collection<GnssMeasurement>gnssMeasurements =event.getMeasurements();
            try {
                WriteGnssClockToFile(gnssClock,gnssMeasurements);
            }catch (IOException e) {
                logException(ERROR_WRITING_FILE, e);
            }

            for (GnssMeasurement gnssMeasurement : gnssMeasurements) {
                try {
                    writeGnssMeasurementToFile(gnssClock, gnssMeasurement);
                } catch (IOException e) {
                    logException(ERROR_WRITING_FILE, e);
                }
            }
        }
    }

    private void WriteGnssClockToFile(GnssClock gnssClock,Collection<GnssMeasurement> gnssMeasurements)
            throws IOException{
        GpsTime Gtime=new GpsTime(gnssClock);
        Log.d(TAG,"Start write clock");
        int year = Gtime.getYear();
        int mon = Gtime.getMonth();
        int date = Gtime.getDate();
        int hour = Gtime.getHour();
        int min = Gtime.getMin();
        double sec = Gtime.getSec();
        int num=0;
        String ASvid="";
        for (GnssMeasurement gnssMeasurement : gnssMeasurements){
            boolean ibad=MeasurementFilter(gnssClock,gnssMeasurement);
            if(!ibad) {
                ASvid += "G" + gnssMeasurement.getSvid();
                num++;
            }
        }
        String line=String.format("%3d%3d%3d%3d%3d%11.7f%3d%3d",year-2000,mon,date,hour,min,sec,0,num);
        mFileWriter.write(line+ASvid+"\n");
        Log.d(TAG,"write clock Success!");
    }

    private void writeGnssMeasurementToFile(GnssClock clock, GnssMeasurement gnssMeasurement)
            throws IOException {
        boolean ibad=MeasurementFilter(clock,gnssMeasurement);
        if(!ibad) {
            Measurement measurement = new Measurement(gnssMeasurement, clock);
            Log.d(TAG, "Start write measurement!");
            double C1 = measurement.getC1();
            double L1 = measurement.getL1();
            double S1 = measurement.getS1();
            String line = String.format("%14.3f%16.3f%16.3f\n", C1, L1, S1);
            mFileWriter.write(line);
            Log.d(TAG, "Write measurement Success!");
        }
    }

    @Override
    public void onGnssMeasurementsStatusChanged(int status) {}

    @Override
    public void onGnssNavigationMessageReceived(GnssNavigationMessage navigationMessage) {/*
        synchronized (mFileLock) {
            if (mFileWriter == null) {
                return;
            }
            StringBuilder builder = new StringBuilder("Nav");
            builder.append(RECORD_DELIMITER);
            builder.append(navigationMessage.getSvid());
            builder.append(RECORD_DELIMITER);
            builder.append(navigationMessage.getType());
            builder.append(RECORD_DELIMITER);

            int status = navigationMessage.getStatus();
            builder.append(status);
            builder.append(RECORD_DELIMITER);
            builder.append(navigationMessage.getMessageId());
            builder.append(RECORD_DELIMITER);
            builder.append(navigationMessage.getSubmessageId());
            byte[] data = navigationMessage.getData();
            for (byte word : data) {
                builder.append(RECORD_DELIMITER);
                builder.append(word);
            }
            try {
                mFileWriter.write(builder.toString());
                mFileWriter.newLine();
            } catch (IOException e) {
                logException(ERROR_WRITING_FILE, e);
            }
        }
    */}

    @Override
    public void onGnssNavigationMessageStatusChanged(int status) {}

    @Override
    public void onGnssStatusChanged(GnssStatus gnssStatus) {}

    @Override
    public void onNmeaReceived(long timestamp, String s) {}

    @Override
    public void onListenerRegistration(String listener, boolean result) {}

    private void logException(String errorMessage, Exception e) {
        Log.e(GnssContainer.TAG + TAG, errorMessage, e);
        Toast.makeText(mContext, errorMessage, Toast.LENGTH_LONG).show();
    }

    private void logError(String errorMessage) {
        Log.e(GnssContainer.TAG + TAG, errorMessage);
        Toast.makeText(mContext, errorMessage, Toast.LENGTH_LONG).show();
    }

    /**
     * Implements a {@link FileFilter} to delete files that are not in the
     * {@link FileToDeleteFilter#mRetainedFiles}.
     */
    private static class FileToDeleteFilter implements FileFilter {
        private final List<File> mRetainedFiles;

        public FileToDeleteFilter(File... retainedFiles) {
            this.mRetainedFiles = Arrays.asList(retainedFiles);
        }

        /**
         * Returns {@code true} to delete the file, and {@code false} to keep the file.
         *
         * Files are deleted if they are not in the {@link FileToDeleteFilter#mRetainedFiles} list.
         */
        @Override
        public boolean accept(File pathname) {
            if (pathname == null || !pathname.exists()) {
                return false;
            }
            if (mRetainedFiles.contains(pathname)) {
                return false;
            }
            return pathname.length() < MINIMUM_USABLE_FILE_SIZE_BYTES;
        }
    }

    private boolean MeasurementFilter(GnssClock gnssClock,GnssMeasurement gnssMeasurement){
        boolean iBad;
        boolean iRcTime;
        boolean iPrRate;
        boolean iCn0DbHz;
        boolean nGps;
        boolean iADRU;
        boolean iPr;
        iRcTime=gnssMeasurement.getReceivedSvTimeUncertaintyNanos()>MAXTOWUNCNS;
        iPrRate=gnssMeasurement.getPseudorangeRateUncertaintyMetersPerSecond()>MAXPRRUNCMPS;
        iCn0DbHz=gnssMeasurement.getCn0DbHz()<MINCN0DBHZ;
        nGps=gnssMeasurement.getSvid()>MAXGPSSVID|gnssMeasurement.getConstellationType()>1;
        iADRU=gnssMeasurement.getAccumulatedDeltaRangeUncertaintyMeters()>0.01|gnssMeasurement.getAccumulatedDeltaRangeState()>1;
        Measurement measurement=new Measurement(gnssMeasurement,gnssClock);
        iPr=measurement.getC1()>99999999.999|measurement.getC1()<=0;
        iBad=iRcTime|iPrRate|iCn0DbHz|nGps|iADRU|iPr;
        return iBad;
    }
}
