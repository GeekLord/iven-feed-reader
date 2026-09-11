package com.iven.lfflfeedreader.mainact;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import com.iven.lfflfeedreader.R;
import com.iven.lfflfeedreader.domparser.DOMParser;
import com.iven.lfflfeedreader.domparser.RSSFeed;
import com.iven.lfflfeedreader.utils.Preferences;
import com.iven.lfflfeedreader.utils.saveUtils;

public class SplashActivity extends AppCompatActivity {

    public static String default_feed_value;
    RSSFeed lfflfeed;
    ConnectivityManager connectivityManager;
    private AsyncLoadXMLFeed feedLoadTask;
    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        default_feed_value = saveUtils.getFeedUrl(this);
        Preferences.applyNavTint(this);
        Preferences.applyLightIcons(this);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager.getActiveNetworkInfo() == null) {
            showNoInternetAndFinish();
        } else {
            loadFeed();
        }
    }

    private void loadFeed() {
        setContentView(R.layout.splash);
        feedLoadTask = new AsyncLoadXMLFeed();
        feedLoadTask.execute();
    }

    private void showNoInternetAndFinish() {
        setContentView(R.layout.splash_no_internet);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    finish();
                }
            }
        }, 2000);
    }

    private boolean canUpdateUi() {
        return !isFinishing() && !isDestroyed();
    }

    private void startListActivity(RSSFeed feed) {
        if (!canUpdateUi()) {
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putSerializable("feed", feed);
        Intent intent = new Intent(this, ListActivity.class);
        intent.putExtras(bundle);
        startActivity(intent);
        finish();
    }

    private void showFeedLoadFailure() {
        if (!canUpdateUi()) {
            return;
        }
        setContentView(R.layout.splash_feed_error);
        Button retry = (Button) findViewById(R.id.retry_feed_load);
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (connectivityManager.getActiveNetworkInfo() == null) {
                    showNoInternetAndFinish();
                    return;
                }
                loadFeed();
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (feedLoadTask != null) {
            feedLoadTask.cancel(true);
        }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private class AsyncLoadXMLFeed extends AsyncTask<Void, Void, RSSFeed> {
        @Override
        protected RSSFeed doInBackground(Void... params) {
            return new DOMParser().parseXml(default_feed_value);
        }

        @Override
        protected void onPostExecute(RSSFeed feed) {
            super.onPostExecute(feed);
            if (isCancelled() || !canUpdateUi()) {
                return;
            }
            lfflfeed = feed;
            if (feed != null && feed.getItemCount() > 0) {
                startListActivity(feed);
            } else {
                showFeedLoadFailure();
            }
        }
    }
}
