package moodify.com.moodify;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerNotificationCallback;
import com.spotify.sdk.android.player.PlayerState;
import com.spotify.sdk.android.player.Spotify;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Playlist;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class MainActivity extends AppCompatActivity implements
        PlayerNotificationCallback, ConnectionStateCallback, SensorEventListener{

    // TODO: Replace with your client ID
    private static final String CLIENT_ID = "6106c54c402f4d878df000badf3384bf";
    // TODO: Replace with your redirect URI
    private static final String REDIRECT_URI = "moodify-auth://callback";

    private Player mPlayer;
    private String oauthToken;

    private static final int REQUEST_CODE = 1337;

    private static String playlists[] = { "spotify:user:jalvarado91:playlist:4dUVDjuZsVHPFkQwnyJfrk" };

    public Button playButton;
    public Button pauseButton;
    public ImageView playlistArtImage;
    public TextView moodLabel;
    public TextView playlistLable;
    public TextView ownerLable;
    public Button setTheMoodButton;

    private SharedPreferences sharedPreferences;
    private SpotifyApi spotifyApi;

    public boolean hasPlayed = false;

    private SensorManager mSensorManager;
    private Sensor mLight, mTemp;
    public String mood;

    public Float currentLightLevel = 10000.00f;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sharedPreferences = getSharedPreferences("com.moodify.PREFERENCE_FILES", MODE_PRIVATE);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mLight = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);


        playButton = (Button) findViewById(R.id.play_button);
        pauseButton = (Button) findViewById(R.id.pause_button);
        playlistArtImage = (ImageView) findViewById(R.id.playlist_art);
        moodLabel = (TextView) findViewById(R.id.mood_label);
        playlistLable = (TextView) findViewById(R.id.playlist_label);
        ownerLable = (TextView) findViewById(R.id.owner_label);
        setTheMoodButton = (Button) findViewById(R.id.set_the_mood_button);

        spotifyApi = new SpotifyApi();


        String token = sharedPreferences.getString("token", "default");
        Log.v("BRO", String.valueOf(sharedPreferences.contains("token")));
        Log.v("BRO", token);

        if(sharedPreferences.getString("token", null) == null || sharedPreferences.getString("token", "").isEmpty()) {
            AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                    AuthenticationResponse.Type.TOKEN,
                    REDIRECT_URI);
            builder.setScopes(new String[]{"user-read-private", "streaming"});
            AuthenticationRequest request = builder.build();
            AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);
        }

        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!hasPlayed) {
                    try {
                        playNow("tbt");
                        hasPlayed = true;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                else {
                    playOrPause("play");
                }
            }
        });

        setTheMoodButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSensorManager.registerListener(MainActivity.this, mLight, SensorManager.SENSOR_DELAY_NORMAL);
            }
        });



        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playOrPause("pause");
            }
        });


    }

    @Override
    protected void onPause() {
        super.onPause();
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Check if result comes from the correct activity
        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, data);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                oauthToken = response.getAccessToken();
                sharedPreferences.edit().putString("token", oauthToken).commit();
                spotifyApi.setAccessToken(sharedPreferences.getString("token", null));
            }
        }
    }

    public void playOrPause(String command) {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("http://192.168.3.163:3000/"+command)
                .build();

        client.newCall(request).enqueue(new com.squareup.okhttp.Callback() {
            @Override
            public void onFailure(Request request, IOException e) {

            }

            @Override
            public void onResponse(com.squareup.okhttp.Response response) throws IOException {
                if(!response.isSuccessful()) throw new IOException("WUT");

                String responseString = response.body().string();
                try {
                    JSONObject responseObject = new JSONObject(responseString);
                    final String status = responseObject.getString("status");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, status, Toast.LENGTH_SHORT).show();
                        }
                    });

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void playNow(String mood) throws Exception {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("http://192.168.3.163:3000/mood/"+mood)
                .build();

        client.newCall(request).enqueue(new com.squareup.okhttp.Callback() {
            @Override
            public void onFailure(Request request, IOException e) {

            }

            @Override
            public void onResponse(com.squareup.okhttp.Response response) throws IOException {
                if(!response.isSuccessful()) throw new IOException("Unexpected code");
                String responseString = response.body().string();
                try {
                    JSONObject responseObject = new JSONObject(responseString);
                    final String mood = responseObject.getString("mood");
                    String rawPlaylistURL = responseObject.getString("playlistURL");

                    String PlaylistURL = rawPlaylistURL.substring(
                            0, rawPlaylistURL.indexOf("\u001b")
                    );

                    String[] tokens = PlaylistURL.split(":");
                    String user = tokens[2];
                    String playlist = tokens[4];

                    SpotifyService spotify = spotifyApi.getService();

                    spotify.getPlaylist(user, playlist, new Callback<Playlist>() {
                        @Override
                        public void success(Playlist playlist, Response response) {

                            try {
                                moodLabel.setText(mood.toUpperCase() + " MOOD");
                                playlistLable.setText(playlist.name);
                                if(playlist.owner.display_name == null || playlist.owner.display_name.isEmpty()) {
                                    ownerLable.setVisibility(View.GONE);
                                } else {
                                    ownerLable.setText(playlist.owner.display_name);
                                }
                                Glide.with(MainActivity.this)
                                        .load(playlist.images.get(0).url)
                                        .into(playlistArtImage);

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }



                        @Override
                        public void failure(RetrofitError error) {

                        }
                    });

                } catch (JSONException e) {
                    e.printStackTrace();
                }


//                Log.v("BRO", mood);
            }
        });
    }

    @Override
    public void onLoggedIn() {
        Log.d("MainActivity", "User logged in");
    }

    @Override
    public void onLoggedOut() {
        Log.d("MainActivity", "User logged out");
    }

    @Override
    public void onLoginFailed(Throwable error) {
        Log.d("MainActivity", "Login failed");
    }

    @Override
    public void onTemporaryError() {
        Log.d("MainActivity", "Temporary error occurred");
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d("MainActivity", "Received connection message: " + message);
    }

    @Override
    public void onPlaybackEvent(EventType eventType, PlayerState playerState) {
        Log.d("MainActivity", "Playback event received: " + eventType.name());
    }

    @Override
    public void onPlaybackError(ErrorType errorType, String errorDetails) {
        Log.d("MainActivity", "Playback error received: " + errorType.name());
    }

    @Override
    protected void onDestroy() {
        Spotify.destroyPlayer(this);
        sharedPreferences.edit().remove("token").apply();
        super.onDestroy();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;
        if (sensor.getType() == Sensor.TYPE_LIGHT)
        {
            Float lightValue = event.values[0];

            Log.v("light", String.valueOf(lightValue));
            Log.v("currentValue", String.valueOf(currentLightLevel));
            if(Math.abs(currentLightLevel - lightValue) < 40){
                return;
            }

            if (currentLightLevel <= 66)
            {
                mood = "jazz";
            }
            else if( currentLightLevel > 66 && currentLightLevel <= 132)
            {
                mood = "tbt";
            }
            else
            {
                mood = "happy";
            }

            currentLightLevel = lightValue;
        }


        try {
            playNow(mood);
            hasPlayed = true;
            setTheMoodButton.setVisibility(View.GONE);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
