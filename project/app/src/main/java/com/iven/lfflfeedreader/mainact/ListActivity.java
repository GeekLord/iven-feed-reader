package com.iven.lfflfeedreader.mainact;

import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ContextThemeWrapper;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.iven.lfflfeedreader.R;
import com.iven.lfflfeedreader.domparser.DOMParser;
import com.iven.lfflfeedreader.domparser.RSSFeed;
import com.iven.lfflfeedreader.domparser.RSSItem;
import com.iven.lfflfeedreader.infoact.AboutActivity;
import com.iven.lfflfeedreader.infoact.InfoActivity;
import com.iven.lfflfeedreader.utils.HomeUtils;
import com.iven.lfflfeedreader.utils.Preferences;
import com.iven.lfflfeedreader.utils.notifyService;
import com.iven.lfflfeedreader.utils.saveUtils;

import java.util.ArrayList;
import java.util.List;

public class ListActivity extends AppCompatActivity implements android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener {

    //feed
    RSSFeed fFeed;
    String feedCustom;
    String feedCustom2;
    String feedURL;
    private int feedRequestId;

    //for notifications
    RSSItem firstItemDate;
    String lastDate;
    String lastDateFormat;

    //view
    SwipeRefreshLayout swipeRefresh;
    ContextThemeWrapper contextThemeWrapper;
    ActionBarDrawerToggle mDrawerToggle;
    Toolbar toolbar;
    DrawerLayout mDrawerLayout;
    Toast toast;

    //RecyclerViews
    RecyclerView recyclerView, customFeedsRecyclerView;
    FeedsAdapter feedsAdapter;
    DynamicRVAdapter dynamicRVAdapter;

    //db
    SQLiteDatabase customFeedsDB;
    Cursor cursor;
    Cursor cursor2;
    Cursor cursor3;
    String table1;
    String table2;
    String whereClause_url;
    String whereClause_feed;
    String[] whereArgs_url;
    String[] whereArgs_name;

    //menu
    Menu menu;
    MenuItem addFeed;

    //Connectivity manager
    ConnectivityManager connectivityManager;

    //notification
    Intent notificationIntent;
    Intent broadcastIntent;

    private List<String> mUrls;
    private List<String> mFeeds;

    //create the toolbar's menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        this.menu = menu;

        getMenuInflater().inflate(R.menu.activity_main, menu);

        //initialize add feed menu items
        addFeed = menu.findItem(R.id.addfeed);

        return super.onCreateOptionsMenu(menu);

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        feedURL = SplashActivity.default_feed_value;

        //initialize the feeds items
        fFeed = (RSSFeed) getIntent().getExtras().get("feed");

        //initialize connectivity manager
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        //apply preferences

        //apply activity's theme if dark theme is enabled
        contextThemeWrapper = new ContextThemeWrapper(getBaseContext(), this.getTheme());
        Preferences.applyTheme(contextThemeWrapper, getBaseContext());

        //set the navbar tint if the preference is enabled
        Preferences.applyNavTint(this);

        //set LightStatusBar
        Preferences.applyLightIcons(this);

        //set the view
        setContentView(R.layout.home);

        //initialize the toolbar
        toolbar = (Toolbar) findViewById(R.id.toolbar);

        //set the toolbar
        setSupportActionBar(toolbar);

        //set the toolbar's title
        toolbar.setTitle(R.string.app_name);

        //set the menu's toolbar click listener
        toolbar.setOnMenuItemClickListener(
                new Toolbar.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {

                        final int mItemId = item.getItemId();

                        //share button using share intent
                        switch (mItemId) {

                            case R.id.option:

                                //close DrawerLayout
                                closeDrawer();

                                //open Settings Activity
                                Intent openSettings = new Intent(ListActivity.this, InfoActivity.class);
                                startActivity(openSettings);
                                break;

                            case R.id.info:
                                //open Settings Activity
                                Intent openInfo = new Intent(ListActivity.this, AboutActivity.class);
                                startActivity(openInfo);
                                break;

                            //this is the button to add feed
                            case R.id.addfeed:

                                //call addFeed method
                                addFeed();
                                break;
                        }

                        return false;
                    }
                });

        //initialize our navigation drawer

        //initialize the Drawer Layout
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        //Tie DrawerLayout events to the ActionBarToggle:
        //it adds the hamburger icon and handle the drawer slide event
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, toolbar, R.string.app_name,
                R.string.app_name) {

            //Called when the drawer is opened
            public void onDrawerOpened(View drawerView) {

                super.onDrawerOpened(drawerView);

                //change toolbar title to 'Add a feed'
                toolbar.setTitle(getResources().getString(R.string.feedmenu));

                //show the add feed menu option
                HomeUtils.showAddFeed(addFeed);
            }

            //Called when the drawer is closed
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                //change toolbar title to the app's name
                toolbar.setTitle(getResources().getString(R.string.app_name));

                //hide the add feed menu option
                HomeUtils.hideAddFeed(addFeed);
            }

        };

        //this handle the hamburger animation
        mDrawerLayout.addDrawerListener(mDrawerToggle);

        //initialize swipe to refresh layout
        swipeRefresh = (SwipeRefreshLayout) findViewById(R.id.swipeRefreshLayout);

        //set on refresh listener
        swipeRefresh.setOnRefreshListener(this);

        //set the articles ListView and the dynamic ListView for custom feeds

        //let's start with the home's ListView

        //initialize the main ListView where items will be added
        recyclerView = (RecyclerView) findViewById(R.id.recyclerView);

        //set the main RecyclerView custom adapter
        feedsAdapter = new FeedsAdapter(this, fFeed);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(feedsAdapter);

        //get the date of the last article posted
        firstItemDate = fFeed.getItem(0);
        lastDate = firstItemDate.getDate();

        //get last five characters and remove the `:`
        lastDateFormat = lastDate.substring(lastDate.length() - 5).replace(":", "");

        notificationIntent = new Intent(ListActivity.this, notifyService.class);

        //send date info to notify service
        notificationIntent.putExtra(notifyService.PARAM_IN_MSG, lastDateFormat);

        //start service if notifications are enabled
        if (Preferences.notificationsEnabled(ListActivity.this)) {

            ListActivity.this.startService(notificationIntent);

        } else {

            //stop service if notifications are disabled
            ListActivity.this.stopService(notificationIntent);
        }


        //initialize the dynamic ListView
        customFeedsRecyclerView = (RecyclerView) findViewById(R.id.recyclerViewDynamic);

        //initialize a sqlite database
        //here we open or create a sqlite database where we are going to add 2 tables with the feeds values
        customFeedsDB = ListActivity.this.openOrCreateDatabase("feedsDB", MODE_PRIVATE, null);

        //create a table called feedslist with a column called "url" where items (the feeds urls) will be stored
        customFeedsDB.execSQL("CREATE TABLE IF NOT EXISTS feedslist (id INTEGER PRIMARY KEY AUTOINCREMENT,url varchar);");

        //create a table called subtitles list with a column called "name" where items (the feeds names) will be stored
        customFeedsDB.execSQL("CREATE TABLE IF NOT EXISTS subtitleslist (id INTEGER PRIMARY KEY AUTOINCREMENT,name varchar);");

        //using a cursor we're going to read the tables
        cursor2 = customFeedsDB.rawQuery("SELECT * FROM feedslist;", null);
        cursor3 = customFeedsDB.rawQuery("SELECT * FROM subtitleslist;", null);

        //we create two new array list to be populated with the tables items (names & urls)

        //for feeds urls
        mUrls = new ArrayList<>();

        //for feeds names
        mFeeds = new ArrayList<>();

        if (cursor2 != null && cursor2.moveToFirst()) {

            while (!cursor2.isAfterLast()) {

                //add items from column "url" into dynamic list
                mUrls.add(cursor2.getString(cursor2.getColumnIndex("url")));
                cursor2.moveToNext();
            }
            cursor2.close();
        }

        if (cursor3 != null && cursor3.moveToFirst()) {

            while (!cursor3.isAfterLast()) {

                //add items from column "name" into dynamic list
                mFeeds.add(cursor3.getString(cursor3.getColumnIndex("name")));
                cursor3.moveToNext();
            }
            cursor3.close();
        }

        //initialize the dynamic RecyclerView adapter
        dynamicRVAdapter = new DynamicRVAdapter();

        customFeedsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        customFeedsRecyclerView.setHasFixedSize(true);

        //set the custom adapter
        customFeedsRecyclerView.setAdapter(dynamicRVAdapter);

    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        //sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopService(notificationIntent);

    }

    //Custom methods starts here

    //method to add feeds inside the db and the dynamic ListView
    public void addFeed() {
        //dialog view
        final ViewGroup nullParent = null;

        final View dialogView = ListActivity.this.getLayoutInflater().inflate(R.layout.add_feed_layout, nullParent);

        new AlertDialog.Builder(ListActivity.this)

                .setTitle(R.string.adddialogtitle)
                .setView(dialogView)

                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        //on click ok we get the text inputs:

                        //get the feeds urls from the input and add to array list
                        final EditText edt1 = (EditText) dialogView.findViewById(R.id.txt1);
                        feedCustom = edt1.getText().toString();
                        mUrls.add(feedCustom);

                        //get the feeds names from the input and add to array list
                        final EditText edt2 = (EditText) dialogView.findViewById(R.id.txt2);
                        feedCustom2 = edt2.getText().toString();
                        mFeeds.add(feedCustom2);

                        //and we update the ListView to add the new item
                        dynamicRVAdapter.notifyDataSetChanged();
                        customFeedsRecyclerView.setAdapter(dynamicRVAdapter);

                        //populate the tables of the sqlite database with these values

                        //fill the urls column
                        customFeedsDB.execSQL("insert into feedslist (url) values(?);", new String[]{feedCustom});

                        //fill the names column
                        customFeedsDB.execSQL("insert into subtitleslist (name) values(?);", new String[]{feedCustom2});
                    }
                })

                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    //method to remove feeds inside the db and the dynamic ListView
    public void removedatFeed(int pos) {

        //on positive click we delete the feed from selected position

        //we're gonna delete them from the db
        //how?

        //using a cursor we select items from the two tables
        cursor = customFeedsDB.rawQuery("SELECT * FROM feedslist;", null);
        cursor2 = customFeedsDB.rawQuery("SELECT * FROM subtitleslist;", null);

        //set these values dynamically to avoid "column index out of range" errors

        String url = "";
        String name = "";

        //set url
        if (cursor != null && cursor.moveToFirst()) {

            while (!cursor.isAfterLast()) {

                //get items at selected position
                url = mUrls.get(pos);
                cursor.moveToNext();
            }
            cursor.close();
        }

        //set feed name
        if (cursor2 != null && cursor2.moveToFirst()) {

            while (!cursor2.isAfterLast()) {

                //we get items at selected position
                name = mFeeds.get(pos);
                cursor2.moveToNext();
            }
            cursor2.close();
        }

        //set the names of the two tables
        table1 = "feedslist";
        table2 = "subtitleslist";

        //set where clause
        whereClause_url = "url" + "=?";
        whereClause_feed = "name" + "=?";

        //set the where arguments
        whereArgs_url = new String[]{String.valueOf(url)};
        whereArgs_name = new String[]{String.valueOf(name)};

        //delete 'em all
        customFeedsDB.delete(table1, whereClause_url, whereArgs_url);
        customFeedsDB.delete(table2, whereClause_feed, whereArgs_name);

        //remove items from the dynamic ListView

        //for url
        mUrls.remove(pos);

        //for feed name
        mFeeds.remove(pos);

        //and update the dynamic list
        //don't move this method above the db deletion method or you'll get javalangindexoutofboundsexception-invalid-index error
        dynamicRVAdapter.notifyDataSetChanged();

        customFeedsRecyclerView.setAdapter(dynamicRVAdapter);
    }

    //create a pending Runnable that runs in background to close the drawer smoothly
    private void closeDrawer() {

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                mDrawerLayout.closeDrawer(GravityCompat.START);

                //hide the add feed menu option
                HomeUtils.hideAddFeed(addFeed);

            }
        }, 200);
    }

    // Loads a feed in the background. Only the newest request may update the UI.
    private void openNewFeed(final String datfeed) {
        closeDrawer();
        final int requestId = ++feedRequestId;
        swipeRefresh.setRefreshing(true);

        if (connectivityManager.getActiveNetworkInfo() == null) {
            toast = Toast.makeText(getBaseContext(), R.string.no_internet, Toast.LENGTH_SHORT);
            toast.show();
            swipeRefresh.setRefreshing(false);
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                final RSSFeed parsedFeed = new DOMParser().parseXml(datfeed);
                ListActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (requestId != feedRequestId || isFinishing()) {
                            return;
                        }
                        if (parsedFeed != null && parsedFeed.getItemCount() > 0) {
                            fFeed = parsedFeed;
                            feedsAdapter = new FeedsAdapter(ListActivity.this, parsedFeed);
                            recyclerView.setAdapter(feedsAdapter);
                            feedURL = HomeUtils.setFeedString(ListActivity.this, datfeed);
                            updateNotificationBaseline(parsedFeed);
                        } else {
                            Toast.makeText(ListActivity.this, R.string.feed_load_failed, Toast.LENGTH_SHORT).show();
                        }
                        swipeRefresh.setRefreshing(false);
                    }
                });
            }
        }).start();
    }

    @Override
    public void onRefresh() {
        final int requestId = ++feedRequestId;
        final String requestedFeedUrl = feedURL;
        if (connectivityManager.getActiveNetworkInfo() == null) {
            Toast.makeText(getBaseContext(), R.string.no_internet, Toast.LENGTH_SHORT).show();
            swipeRefresh.setRefreshing(false);
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                final RSSFeed parsedFeed = new DOMParser().parseXml(requestedFeedUrl);
                ListActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (requestId != feedRequestId || isFinishing()) {
                            return;
                        }
                        if (parsedFeed != null && parsedFeed.getItemCount() > 0) {
                            fFeed = parsedFeed;
                            feedsAdapter = new FeedsAdapter(ListActivity.this, parsedFeed);
                            recyclerView.setAdapter(feedsAdapter);
                        } else {
                            Toast.makeText(ListActivity.this, R.string.feed_load_failed, Toast.LENGTH_SHORT).show();
                        }
                        swipeRefresh.setRefreshing(false);
                    }
                });
            }
        }).start();
    }

    private void updateNotificationBaseline(RSSFeed feed) {
        if (!isNotificationServiceRunning(notifyService.class)) {
            return;
        }
        firstItemDate = feed.getItem(0);
        lastDate = firstItemDate.getDate();
        lastDateFormat = lastDate.substring(lastDate.length() - 5).replace(":", "");
        saveUtils.saveLastDate(getBaseContext(), lastDateFormat);
        stopService(notificationIntent);
        broadcastIntent = new Intent("dontKillMe");
        sendBroadcast(broadcastIntent);
    }

    //used to check if notify service is running
    //https://stackoverflow.com/questions/17588910/check-if-service-is-running-on-android
    private boolean isNotificationServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    //Dynamic RecyclerView adapter
    class DynamicRVAdapter extends RecyclerView.Adapter<DynamicRVAdapter.SimpleViewHolder> {

        DynamicRVAdapter() {

        }

        @Override
        public SimpleViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

            View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.dynamic_items, parent, false);
            return new SimpleViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(SimpleViewHolder holder, int position) {

            //get title and subtitle of custom feeds recycler view
            holder.title.setText(mFeeds.get(position));

            holder.subtitle.setText(mUrls.get(position));
        }

        @Override
        public int getItemCount() {

            //get the size of list
            return mUrls.size();
        }

        //simple view holder implementing on click and on long click
        class SimpleViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

            private TextView title, subtitle;

            SimpleViewHolder(View itemView) {
                super(itemView);

                this.title = (TextView) itemView.findViewById(R.id.title_dyn);
                this.subtitle = (TextView) itemView.findViewById(R.id.subtitle_dyn);

                itemView.setOnClickListener(this);
                itemView.setOnLongClickListener(this);

            }

            @Override
            public void onClick(View v) {

                //on click load the selected feed
                openNewFeed(mUrls.get(getAdapterPosition()));
            }

            @Override
            public boolean onLongClick(View v) {

                //on long click ask user if he wants to remove dat feed

                //Disable RecyclerView clicks to avoid unwanted clicks while deleting feeds
                customFeedsRecyclerView.setEnabled(false);

                //on long click we create a new alert dialog

                new AlertDialog.Builder(ListActivity.this)

                        .setTitle(R.string.deletedialogtitle)
                        .setMessage(getResources().getString(R.string.deletedialogquestion) + " '" + mFeeds.get(getAdapterPosition()) + "' ?")

                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                //on positive click we delete the feed from selected position

                                //we're gonna delete them from the db calling this method:
                                removedatFeed(getAdapterPosition());

                                //enable RecyclerView clicks
                                customFeedsRecyclerView.setEnabled(true);
                            }
                        })
                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {

                                //enable ListView clicks
                                customFeedsRecyclerView.setEnabled(true);
                            }
                        })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                //enable ListView clicks
                                customFeedsRecyclerView.setEnabled(true);
                            }
                        })

                        .show();

                return false;
            }
        }
    }
}


