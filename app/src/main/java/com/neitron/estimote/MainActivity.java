package com.neitron.estimote;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.PowerManager;
import android.os.RemoteException;

import android.support.v7.app.ActionBarActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends ActionBarActivity {

    private static final String ESTIMOTE_PROXIMITY_UUID = "B9407F30-F5F8-466E-AFF9-25556B57FE6D";

    private static final Region ALL_ESTIMOTE_BEACONS = new Region("regionId", ESTIMOTE_PROXIMITY_UUID, null, null);

    private static final Region first_ESTIMOTE_BEACONS = new Region("regionId", ESTIMOTE_PROXIMITY_UUID, 31826 /*Major*/, 37327 /*Minor*/);
    private static final Region second_ESTIMOTE_BEACONS = new Region("regionId", ESTIMOTE_PROXIMITY_UUID, 58146, 9076);
    private static final Region third_ESTIMOTE_BEACONS = new Region("regionId", ESTIMOTE_PROXIMITY_UUID, 10268, 48255);

    private static final String TAG = MainActivity.class.getName();

    private BeaconManager beaconManager = new BeaconManager(this); // beacon manager for connect

    Beacon nearBeacon = null; //nearest beacon in region

    private int estimotesCountInRegion = 0;

    DB dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DB(this, "estimote", null, 1);

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        beaconManager.setBackgroundScanPeriod(TimeUnit.SECONDS.toMillis(1), 0);

        beaconManager.setMonitoringListener(new BeaconManager.MonitoringListener() {
            @Override
            public void onEnteredRegion(Region region, List<Beacon> beacons) {
                //postNotification("Entered region");
                //Log.d("Qwer", "ENTERED");
                if(!beacons.isEmpty() && !beacons.get(0).equals(nearBeacon)) {
                    nearBeacon = beacons.get(0);

                    GetData gd = new GetData();
                    gd.execute(nearBeacon);
                }

                ++estimotesCountInRegion;
            }

            @Override
            public void onExitedRegion(Region region){
                if(--estimotesCountInRegion == 0) {
                    postNotification("Exited region", null);
                }
            }

        });
//        beaconManager.setRangingListener(
//                // колбэк когда найден бикон
//            new BeaconManager.RangingListener(){
//                @Override // сообщает о маяках в регионе отсортированых по точности
//                public void onBeaconsDiscovered(Region region, final List<Beacon> beacons) {
//
//                    if(!beacons.isEmpty() && !beacons.get(0).equals(nearBeacon)) {
//                        nearBeacon = beacons.get(0);
//
//                        GetData gd = new GetData();
//                        gd.execute(nearBeacon);
//                    }
//                }
//            }
//        );
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Check if device supports Bluetooth Low Energy.
        if (!beaconManager.hasBluetooth()) {
            Toast.makeText(this, "Device does not have Bluetooth Low Energy", Toast.LENGTH_LONG).show();
            return;
        }

        // If Bluetooth is not enabled, let user enable it.
        if (!beaconManager.isBluetoothEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1234);
        }

        // Should be invoked in #onStart.
        beaconManager.connect(new BeaconManager.ServiceReadyCallback() {
            @Override // сообщает когда сервис готов к использованию
            public void onServiceReady() {
                try {
                        //beaconManager.startMonitoring(ALL_ESTIMOTE_BEACONS);
                        // Отлавливаем биконы (даже когда телефон заблоирован)
                        beaconManager.startMonitoring(first_ESTIMOTE_BEACONS); // 1 бикон из 3
                        beaconManager.startMonitoring(second_ESTIMOTE_BEACONS); // 2
                        beaconManager.startMonitoring(third_ESTIMOTE_BEACONS); // 3

                    //начинает поиск биконов, результаты отправляются слушателю setRangingListener (я так понял ранж в отдельном потоке)

                    //beaconManager.startRanging(ALL_ESTIMOTE_BEACONS);

                } catch (RemoteException e) {
                    Log.e(TAG, "Cannot start ranging", e);
                }
            }
        });
    }

    private NotificationManager notificationManager;
    private static final int NOTIFICATION_ID = 123;


    private void postNotification(String msg, Bitmap ico)
    {
        Intent notifyIntent = new Intent(MainActivity.this, MainActivity.class);
        notifyIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivities(
                MainActivity.this,
                0,
                new Intent[]{ notifyIntent },
                PendingIntent.FLAG_UPDATE_CURRENT);


        if(ico != null)
        {
            Notification notification = new Notification.Builder(MainActivity.this)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setLargeIcon(ico)
                    .setContentTitle("Estimote")
                    .setContentText(msg)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build();

            notification.defaults |= Notification.DEFAULT_SOUND;
            notification.defaults |= Notification.DEFAULT_LIGHTS;

            notificationManager.notify(NOTIFICATION_ID, notification);

            Log.d(msg, ico.toString());
        }
        else
        {
            Notification notification = new Notification.Builder(MainActivity.this)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Estimote for shops")
                    .setContentText(msg)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build();

            notification.defaults |= Notification.DEFAULT_SOUND;
            notification.defaults |= Notification.DEFAULT_LIGHTS;

            notificationManager.notify(NOTIFICATION_ID, notification);

            Log.d(msg, "");
        }
        //TextView statusTextView = (TextView) findViewById(R.id.status);
        //statusTextView.setText(msg);
    }

    private class GetData extends AsyncTask<Beacon, Beacon, List<String>> {
        Bitmap firstBitmap;

        @Override
        protected void onProgressUpdate(Beacon... beacon) {
            super.onProgressUpdate(beacon[0]);

            TextView tv_j = (TextView) findViewById(R.id.bec_major);
            TextView tv_n = (TextView) findViewById(R.id.bec_minor);
            TextView tv_d = (TextView) findViewById(R.id.bec_distance);

            tv_j.setText("Major: \t" + beacon[0].getMajor());
            tv_n.setText("Minor: \t" + beacon[0].getMinor());
            tv_d.setText("Distance: \t" + beacon[0].getRssi() / -100.0f);
        }

        @Override
        protected void onPostExecute(List<String> str) {

            ConnectivityManager cm = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo nf = cm.getActiveNetworkInfo();

            ContentValues cv = new ContentValues();

            SQLiteDatabase db = dbHelper.getWritableDatabase();

            TextView mes = (TextView) findViewById(R.id.mes);
            TextView url = (TextView) findViewById(R.id.url);
            ImageView im = (ImageView) findViewById(R.id.imageView);

            if(nf != null && nf.isConnected()) {
                Log.d("db", "CONNECTED");

                int clearCount = db.delete("beacon", "beacon_major = " + str.get(4), null);
                Log.d("db", "clear count = " + clearCount);

                cv.put("beacon_major", Integer.parseInt(str.get(4)));
                cv.put("hello_msg", str.get(0));
                cv.put("bye_msg", str.get(2));
                cv.put("image_url", str.get(1));
                cv.put("site_url", str.get(3));

                long id = db.insert("beacon", null, cv);
                Log.d("db", "insert id = " + id);
                Log.d("im",firstBitmap.toString());
                im.setImageBitmap(firstBitmap);

                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), str.get(4) + ".png");

                try {
                    FileOutputStream fos = null;
                    try {
                        fos = new FileOutputStream(file);
                        boolean b = firstBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                        Log.d("image", "" + b);
                    } finally {
                        if (fos != null) fos.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                mes.setText(str.get(0));
                url.setText("Shop`s site: " + str.get(1));


                if (getResources().getDisplayMetrics().densityDpi < 600) {
                    postNotification(str.get(0), firstBitmap);
                } else {

                    startActivity(
                            new Intent(Intent.ACTION_VIEW, Uri.parse(str.get(1)))
                    );
                }
            }
            else
            {
                Log.d("db", "NOT CONNECTED id = " + str.get(0));

                // делаем запрос всех данных из таблицы mytable, получаем Cursor
                Cursor c = db.query("beacon", null, "beacon_major = ?", new String[]{str.get(0)}, null, null, null);
                //Cursor c = db.query("beacon", null, null, null, null, null, null);

                // ставим позицию курсора на первую строку выборки
                // если в выборке нет строк, вернется false
                if (c.moveToFirst()) {

                    // определяем номера столбцов по имени в выборке
                    //int bm = c.getColumnIndex("beacon_major");
                    int hmsg = c.getColumnIndex("hello_msg");
                    //int bmsg = c.getColumnIndex("bye_msg");
                    int iurl = c.getColumnIndex("image_url");
                    int surl = c.getColumnIndex("site_url");

                    File file = new File(Environment.
                            getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), str.get(0) + ".png");
                    firstBitmap = BitmapFactory.decodeFile(file.getAbsolutePath());

                    Log.d("log", String.format("bitmap size = %sx%s, byteCount = %s",
                            firstBitmap.getWidth(), firstBitmap.getHeight(),
                            (int) (firstBitmap.getByteCount() / 1024)));


                    im.setImageBitmap(firstBitmap);

                    mes.setText(c.getString(hmsg));
                    url.setText("Shop`s site: " + c.getString(surl));

                    if (getResources().getDisplayMetrics().densityDpi < 600) {
                        postNotification(c.getString(hmsg), firstBitmap);
                    } else {

                        startActivity(
                                new Intent(Intent.ACTION_VIEW, Uri.parse(c.getString(surl)))
                        );
                    }

                } else
                    Log.d("db", "0 rows");
                c.close();

            }

            dbHelper.close();
        }

        @Override
        protected List<String> doInBackground(Beacon... params) {

            List <String> str = new ArrayList<>();

            Beacon beacon = params[0];

            str.add("" + beacon.getMajor());

            publishProgress(beacon);

            String result;


            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            try {
                // Construct the URL for the OpenWeatherMap query
                // Possible parameters are avaiable at OWM's forecast API page, at
                // http://openweathermap.org/API#forecast
                final String FORECAST_BASE_URL =
                        "http://for-kirill-zubenko.pe.hu/?";

                final String ID_PARAM = "id";

                Uri builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(ID_PARAM, "" + beacon.getMajor())
                        .build();

                URL url = new URL(builtUri.toString());

                // Create the request to OpenWeatherMap, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();

                if (inputStream == null) {
                    // Nothing to do.
                    return null;
                }

                reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder buffer = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed
                    // buffer for debugging.
                    String lineOutput = line + '\n';
                    buffer.append(lineOutput);
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    return null;
                }
                result = buffer.toString();

                Log.d("js", result);

                String imageUrl;
                String shop_url;
                String hello_mes;
                String bye_mes;



                try {

                    JSONObject json = new JSONObject(result);
                    imageUrl = json.getString("image");
                    shop_url = json.getString("shop_url");
                    hello_mes = json.getString("hello_mes");
                    bye_mes = json.getString("bye_mes");

                    firstBitmap = getBitmapFromURL(imageUrl);

                    Log.d("info", "\n" + shop_url + '\n' + hello_mes + '\n' + bye_mes + '\n');

                    str.clear();

                    str.add(hello_mes);
                    str.add(imageUrl);
                    str.add(bye_mes);
                    str.add(shop_url);
                    str.add(""+beacon.getMajor());


                } catch (JSONException e) {
                    Log.e("", e.getMessage(), e);
                    e.printStackTrace();
                }

                return str;

            } catch (IOException e) {
                Log.e("", "Error ", e);
                // If the code didn't successfully get the weather data, there's no point in attemping
                // to parse it.
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e("", "Error closing stream", e);
                    }
                }
            }
            str.clear();
            str.add(""+beacon.getMajor());
            return str;
        }
//            // далее мы должны отправлять запрос на апи сервер с параметрами бикона, и получать джейсон ответ для одного бикона
//            // заглушка
//            switch (beacon.getMajor())
//            {
//                //---------------------------------------------------------------------------------------------------------
//                case 58146:
//                    result =
//                            "{\"hello_mes\":\"Wellcome to adidas shop!\"," +
//                                    "\"bye_mes\": \"Have a nice day\"," +
//                                    "\"image\":\"http://daler.ru/pictures/1/2560x1600/Logotip-Adidas-9677.jpg\"," +
//                                    "\"shop_url\":\"http://adidas.com\"}";
//                    break;
//                //---------------------------------------------------------------------------------------------------------
//                case 31826:
//                    result =
//                            "{\"hello_mes\":\"Wellcome to nike shop!\"," +
//                                    "\"bye_mes\": \"Have a nice day\"," +
//                                    "\"image\":\"http://asiareport.ru/images/stories/company/nike-logo-3d.jpg\"," +
//                                    "\"shop_url\":\"http://nike.com\"}";
//                    break;
//                //---------------------------------------------------------------------------------------------------------
//                case 10268:
//                    result =
//                            "{\"hello_mes\":\"Wellcome to PUMBA!\"," +
//                                    "\"bye_mes\": \"Have a nice day\"," +
//                                    "\"image\":\"http://www.rulez-t.info/uploads/posts/2012-03/1331057457_img_catsmob_com_20120306_00451_038.jpg\"," +
//                                    "\"shop_url\":\"https://ru.wikipedia.org/wiki/%D0%9F%D1%83%D0%BC%D0%B1%D0%B0_(%D0%9A%D0%BE%D1%80%D0%BE%D0%BB%D1%8C_%D0%9B%D0%B5%D0%B2)\"}";
//                    break;
//                //---------------------------------------------------------------------------------------------------------
//                default:
//                    result =
//                            "{}";
//            }

        public Bitmap getBitmapFromURL(String src) {
            try {
                URL url = new URL(src);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();

                Bitmap myBitmap = BitmapFactory.decodeStream(input);
                return myBitmap;

            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Should be invoked in #onStop.
        try {
            beaconManager.stopRanging(ALL_ESTIMOTE_BEACONS);
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot stop but it does not matter now", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // When no longer needed. Should be invoked in #onDestroy.
        beaconManager.disconnect();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}



