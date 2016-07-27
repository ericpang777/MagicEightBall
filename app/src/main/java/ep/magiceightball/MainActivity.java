package ep.magiceightball;

import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.support.v7.app.AppCompatActivity;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements AnimationListener {
    boolean usingMotion = true;
    boolean usingProximity = false;
    float xAccel, yAccel, zAccel; //accel values based on axis
    float Accel = 0.00f;
    float AccelPresent, AccelLast = SensorManager.GRAVITY_EARTH; //negate the effect of gravity on the values
    float xPos, yPos = 0.00f; //stands for xPosition and xVelocity
    float xMax, yMax; //how big the screen is
    int deviceWidth, deviceHeight; //resolution of device
    long start = Long.MAX_VALUE; //if value is too small, will vibrate infinitely
    static final int REQUEST_OK = 1;

    Animation animationStart; 
    Button voiceButton;
    ConnectivityManager mConnectivityManager;
    ImageView mEightBall;
    ImageView background;
    LinearLayout layout;
    Sensor mAccelerometer, mProximity;
    SensorManager mSensorManager;
    String[] answers;
    TextView answer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(ep.magiceightball.R.layout.activity_main);
        background = (ImageView)findViewById(R.id.background);
        mEightBall = (ImageView)findViewById(R.id.eightBall);//sets the eight ball image to an image view
        answer = (TextView)findViewById(R.id.answerLoc);
        voiceButton = (Button)findViewById(R.id.voiceButton);
        voiceButton.setVisibility(View.INVISIBLE); //set invisible on the initial screen

        mSensorManager = (SensorManager)MainActivity.this.getSystemService(Context.SENSOR_SERVICE); //sets up service
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER); //accelerometer uses accelerometer sensor
        mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY); //proximity uses proximity sensor
        mConnectivityManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);//sets up connector

        initAnimation();//beings spinning background
        initAnswer(); //initializes answer array
        getWindowSize(); //gets screen size
        initMotionSensor(); //starts the app off on motion
    }

    //creates overflow menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_items, menu);//inflates menu with items to choose from the menu_items.xml selection
        return super.onCreateOptionsMenu(menu);
    }

    //handles what happens what an item in overflow menu is clicked
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch(id) { //if an item in overflow menu is clicked
            case R.id.motion:
                usingMotion = true;
                if(usingProximity) { //unregisters proximity listeners
                    mSensorManager.unregisterListener(mSensorListener);
                    usingProximity = false;
                }

                initMotionSensor(); //sets up the motion sensors
                voiceButton = (Button)findViewById(R.id.voiceButton);
                voiceButton.setVisibility(View.INVISIBLE);
                break;

            case R.id.proximity:
                usingProximity = true;
                if(usingMotion) {
                    mSensorManager.unregisterListener(mSensorListener);//unregisters motion listeners
                    usingMotion = false;
                }

                initProximitySensor();
                voiceButton = (Button)findViewById(R.id.voiceButton);
                voiceButton.setVisibility(View.INVISIBLE);
                break;

            case R.id.voice:
                if(usingMotion) {
                    mSensorManager.unregisterListener(mSensorListener);//unregisters listeners
                }
                if(usingProximity) {
                    mSensorManager.unregisterListener(mSensorListener);
                }

                usingMotion = false; //this is set after in case motion was never activated, in which then
                //sensors cannot be unregistered
                initButton();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(usingMotion) { //registers listener again when app is resumed, and only if the user was using motion before
            mSensorManager.registerListener(mSensorListener,
                    mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (usingProximity) {
            mSensorManager.registerListener(mSensorListener,
                    mProximity, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(usingMotion) { //unregisters when app is paused
            mSensorManager.unregisterListener(mSensorListener);
        }
        if(usingProximity) {
            mSensorManager.unregisterListener(mSensorListener);
        }
    }

    //initializes steps needed to start animation of the background
    private void initAnimation() {
        animationStart = AnimationUtils.loadAnimation(this, R.anim.rotate);
        animationStart.setAnimationListener(this);
        background.startAnimation(animationStart);
    }

    //checks if the current network is null or not, meaning returns true if no internet
    private boolean hasNoInternet() {
        return mConnectivityManager.getActiveNetworkInfo() == null;
    }

    //initializes voice button to activate voice recognition
    private void initButton() {
        voiceButton = (Button)findViewById(R.id.voiceButton); //finds button
        voiceButton.setVisibility(View.VISIBLE); //sets visibility
        voiceButton.setOnClickListener(new View.OnClickListener() { //sets click listener
            @Override
            public void onClick(View view) {
                getSpeechInput(); // calls this when clicked
            }
        });
    }

    private void getSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);//intent for recognizing speech
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, "en-US"); //adds the language used
        if(hasNoInternet()) { //voice recognizer requires an internet connection
            Toast.makeText(this, "Requires Internet", Toast.LENGTH_SHORT).show();
        }
        try { //checks to see if the phone is able to do speech to text
            startActivityForResult(intent, REQUEST_OK);
        }
        catch (Exception e){
            Toast.makeText(this, "Speech to text failed.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && null != data) { //send back resultcode, if it's successful, also checks if there's data
            ArrayList<String> result =  //possible text results are organized in an arraylist
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            //top of the arraylist is the most confident answer
            boolean foundAnswer = false;
            for (int i = 0; i < result.size() && !foundAnswer; i++) {
                if (result.get(i).contains("tell me")) { //to filter out background noice
                    answer = (TextView) findViewById(R.id.answerLoc);
                    answer.bringToFront();
                    showAnswer();
                    foundAnswer = true;
                }
            }
            if (!foundAnswer) {
                Toast.makeText(this, "Did not say right sentence.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void getWindowSize() {
        //calculates size of window and sets the max x values and y values to that
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        deviceWidth = size.x;
        deviceHeight = size.y;
        xMax = (float)deviceWidth - 50;
        yMax = (float)deviceHeight - 50;
    }

    //initializes accelerometer
    private void initMotionSensor() {
        //registers sensor listeners
        mSensorManager.registerListener(mSensorListener, //sets up listener, giving it the type of sensor and the delay of refresh
                mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);

    }

    //initializes proximity sensor
    private void initProximitySensor() {
        mSensorManager.registerListener(mSensorListener,
                mProximity, SensorManager.SENSOR_DELAY_NORMAL);
    }

    //data from motion listener goes here
    private final SensorEventListener mSensorListener = new SensorEventListener() {
        //detects shaking
        public void onSensorChanged(SensorEvent se) {
            if(se.sensor.getType() == Sensor.TYPE_ACCELEROMETER) { //only accelerometer values
                xAccel = se.values[0]; //x axis movement
                yAccel = se.values[1]; //y axis movement
                zAccel = se.values[2]; //z axis movement

                AccelLast = AccelPresent; //values constantly get pushed back, the last present value is now the value "last" time

                //Pythagorean Theorem to calculate distance moved
                AccelPresent = (float) Math.sqrt(xAccel * xAccel + yAccel * yAccel + zAccel * zAccel);

                float delta = AccelPresent - AccelLast;
                Accel = Accel * 0.9f + delta; //the range of motion detected
                updateBall();//updates ball location depending on user's shaking

                if (Accel > 10) {
                    //when app starts, accel value goes to around 8, this value must be higher so the app doesn't
                    //give an answer the second the app starts
                    start = System.currentTimeMillis(); //a time to compare the delay to
                }
                if ((System.currentTimeMillis() - start) > 400) { //gives answer when shaking dies down, 400ms delay needed
                    showAnswer();//generates answer
                    start = Long.MAX_VALUE; //reset the value to max in order to prevent infinite loop
                }
            }

            if(se.sensor.getType() == Sensor.TYPE_PROXIMITY) {
                float distance = se.values[0]; //distance from user
                if(distance < 5) {
                    answer = (TextView)findViewById(R.id.answerLoc);
                    answer.bringToFront();
                    showAnswer();
                }
            }
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            //Auto-generated method stub
        }
    };

    //updates the ball location on when using accelerometer
    private void updateBall() {

        xPos = xAccel*10; //multiplies values by 20 so the effects can be seen
        yPos = yAccel*10;
        if (xPos > xMax/2) { //keeps location of ball in check
            xPos = xMax/2;
        }

        //same thing for y
        if (yPos > yMax/2) {
            yPos = yMax/2;
        }

        ImageView eightBall = (ImageView)findViewById(R.id.eightBall); //sets location of ball according to xPos
        eightBall.setTranslationX(xPos);
        eightBall.setTranslationY(yPos);

        answer = (TextView)findViewById(R.id.answerLoc); //sets location of answer identical to eight ball
        answer.setTranslationX(xPos);
        answer.setTranslationY(yPos);
    }

    @Override
    public void onAnimationStart(Animation animation) {
        //Auto-generated method stub
    }

    @Override
    public void onAnimationEnd(Animation animation) {
        //Auto-generated method stub
    }

    @Override
    public void onAnimationRepeat(Animation animation) {
        //Auto-generated method stub
    }

    private void vibrate() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE); //sets up vibrator
        vibrator.vibrate(400); //vibrates the phone for 400 milliseconds
    }

    private void showAnswer() {
        answer.setText(answerGenerator()); //sets that text to the answer generator
        answer.setVisibility(View.VISIBLE);
        answer.bringToFront();
        vibrate(); //vibrates the phone indicating an answer
    }

    private String answerGenerator() {
        Random random = new Random();
        int randomInt = random.nextInt(20); //random int between 0 and 19
        return answers[randomInt]; //inputs the random number into the array to get a random answer
    }

    private void initAnswer() {
        //array with the complete answer list for the 8 ball
        answers = new String[]{
                //new line and spacing to fit in the blue triangle

                //positive answers (10)
                "IT IS CERTAIN",
                "IT IS DECIDEDLY SO",
                "WITHOUT A DOUBT",
                "YES, DEFINITELY",
                "YOU MAY RELY ON IT",
                "AS I SEE IT, YES",
                "MOST LIKELY",
                "OUTLOOK GOOD",
                "YES",
                "SIGNS POINT TO YES",

                // neutral answers (5)
                "REPLY HAZY TRY AGAIN",
                "ASK AGAIN LATER",
                "BETTER NOT TELL YOU NOW",
                "CANNOT PREDICT NOW",
                "CONCENTRATE AND ASK AGAIN",

                // negative answers (5)
                "DON'T COUNT ON IT",
                "MY REPLY IS NO",
                "MY SOURCES SAY NO",
                "OUTLOOK NOT SO GOOD",
                "VERY DOUBTFUL"
        };
    }
}
