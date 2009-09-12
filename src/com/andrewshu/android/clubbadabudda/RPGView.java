package com.andrewshu.android.clubbadabudda;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.SurfaceHolder.Callback;
import android.widget.TextView;

public class RPGView extends SurfaceView implements Callback {
    class RPGThread extends Thread {
        /*
         * Difficulty setting constants
         */
        public static final int DIFFICULTY_EASY = 0;
        public static final int DIFFICULTY_HARD = 1;
        public static final int DIFFICULTY_MEDIUM = 2;
        /*
         * Physics constants
         */
        public static final int PHYS_DOWN_ACCEL_SEC = 35;
        public static final int PHYS_FIRE_ACCEL_SEC = 80;
        public static final int PHYS_FUEL_INIT = 60;
        public static final int PHYS_FUEL_MAX = 100;
        public static final int PHYS_FUEL_SEC = 10;
        public static final int PHYS_SLEW_SEC = 120; // degrees/second rotate
        public static final int PHYS_SPEED_HYPERSPACE = 180;
        public static final int PHYS_SPEED_INIT = 30;
        public static final int PHYS_SPEED_MAX = 120;
        /*
         * State-tracking constants
         */
        public static final int STATE_LOSE = 1;
        public static final int STATE_PAUSE = 2;
        public static final int STATE_READY = 3;
        public static final int STATE_RUNNING = 4;
        public static final int STATE_WIN = 5;

        /*
         * Player action constants
         */
        public static final int DIRECTION_LEFT = 0;
        public static final int DIRECTION_RIGHT = 1;
        public static final int DIRECTION_DOWN = 2;
        public static final int DIRECTION_UP = 3;
        public static final int DIRECTION_NONE = -1;
        
        /*
         * Goal condition constants
         */
        public static final int TARGET_ANGLE = 18; // > this angle means crash
        public static final int TARGET_BOTTOM_PADDING = 17; // px below gear
        public static final int TARGET_PAD_HEIGHT = 8; // how high above ground
        public static final int TARGET_SPEED = 28; // > this speed means crash
        public static final double TARGET_WIDTH = 1.6; // width of target
        /*
         * UI constants (i.e. the speed & fuel bars)
         */
        public static final int UI_BAR = 100; // width of the bar(s)
        public static final int UI_BAR_HEIGHT = 10; // height of the bar(s)
        private static final String KEY_DIFFICULTY = "mDifficulty";
        private static final String KEY_FUEL = "mFuel";
        private static final String KEY_GOAL_ANGLE = "mGoalAngle";
        private static final String KEY_GOAL_SPEED = "mGoalSpeed";
        private static final String KEY_GOAL_WIDTH = "mGoalWidth";

        private static final String KEY_GOAL_X = "mGoalX";
        private static final String KEY_LANDER_HEIGHT = "mLanderHeight";
        private static final String KEY_LANDER_WIDTH = "mLanderWidth";
        private static final String KEY_WINS = "mWinsInARow";

        private static final String KEY_X = "mX";
        private static final String KEY_Y = "mY";

        /*
         * Member (state) fields
         */
        /** The drawable to use as the background of the animation canvas */
        private Bitmap mBackgroundImage;

        /**
         * Current height of the surface/canvas.
         * 
         * @see #setSurfaceSize
         */
        private int mCanvasHeight = 1;

        /**
         * Current width of the surface/canvas.
         * 
         * @see #setSurfaceSize
         */
        private int mCanvasWidth = 1;

        /** What to draw for the Lander when it has crashed */
        private Drawable mCrashedImage;

        /**
         * Current difficulty -- amount of fuel, allowed angle, etc. Default is
         * MEDIUM.
         */
        private int mDifficulty;

        /** Is the engine burning? */
        private boolean mEngineFiring;
        
        /** Is the player walking? */
        private boolean mWalking;
        
        private int mFramesPerTileX, mFramesPerTileY;
        private int mCurrentFrame = 0;
        private int mFramesAllowInput = 6;
        
        /** Increment frame and wrap to 0 */
        private void incrementFrame() {
        	synchronized(mSurfaceHolder) {
	        	if (mPlayerDirection == DIRECTION_LEFT || mPlayerDirection == DIRECTION_RIGHT) {
	        		if (mCurrentFrame + 1 >= mFramesPerTileX)
	        			mCurrentFrame = 0;
	        		else
	        			mCurrentFrame++;
	        	} else {
	        		if (mCurrentFrame + 1 >= mFramesPerTileY)
	        			mCurrentFrame = 0;
	        		else
	        			mCurrentFrame++;
	        	}
        	}
        }
        
        /** Walk one of the 4 cardinal directions */
        private void beginWalk() {
        	if (mNextDirection == DIRECTION_NONE)
        		return;
        	// Set current direction
        	setPlayerDirection(mNextDirection);
        	// Unset next direction
        	setPlayerNextDirection(DIRECTION_NONE);
        	// Set walking to true
        	setWalking(true);
        }
        
        private void endWalk() {
        	if (mNextDirection == DIRECTION_NONE)
    			setWalking(false);
    		else {
    			setPlayerDirection(mNextDirection);
    			setPlayerNextDirection(DIRECTION_NONE);
    		}
        }
        
        
        /** Facing which direction */
        private int mPlayerDirection = DIRECTION_DOWN;
        /** Should the player continue motion after moving to the next tile (or whatever unit of motion)? */
        private int mNextDirection = DIRECTION_NONE;
        
        /** What to draw for the Lander when the engine is firing */
        private Drawable mFiringImage;

        /** The player's movement speed */
        private int mPlayerSpeed;
        
        /** Health remaining */
        private int mHealth;

        /** Allowed angle. */
        private int mGoalAngle;

        /** Allowed speed. */
        private int mGoalSpeed;

        /** Width of the landing pad. */
        private int mGoalWidth;

        /** X of the landing pad. */
        private int mGoalX;

        /** Message handler used by thread to interact with TextView */
        private Handler mHandler;

        /** Pixel height of lander image. */
        private static final int mClubbaHeight = 32;
        private static final int mClubbaHalfHeight = 16;

        /** What to draw for the Lander in its normal state */
        private final Drawable[] mClubbaWalkDownImages = new Drawable[2];
        private Drawable mClubbaStandDownImage;

        /** Pixel width of lander image. */
        private static final int mClubbaWidth = 32;
        private static final int mClubbaHalfWidth = 16;

        /** Used to figure out elapsed time between frames */
        private long mLastTime;

        /** Paint to draw the lines on screen. */
        private Paint mLinePaint;

        /** "Bad" speed-too-high variant of the line color. */
        private Paint mLinePaintBad;

        /** The state of the game. One of READY, RUNNING, PAUSE, LOSE, or WIN */
        private int mMode;

        /** Indicate whether the surface has been created & is ready to draw */
        private boolean mRun = false;

        /** Scratch rect object. */
        private RectF mScratchRect;

        /** Handle to the surface manager object we interact with */
        private SurfaceHolder mSurfaceHolder;

        /** Number of wins in a row. */
        private int mWinsInARow;

        /** X of player center. */
        private int mX;

        /** Y of player center. */
        private int mY;

        public RPGThread(SurfaceHolder surfaceHolder, Context context,
                Handler handler) {
            // get handles to some important objects
            mSurfaceHolder = surfaceHolder;
            mHandler = handler;
            mContext = context;

            Resources res = context.getResources();
            // cache handles to our key sprites & other drawables
            mClubbaWalkDownImages[0] = context.getResources().getDrawable(R.drawable.clubba_walk_down1);
            mClubbaWalkDownImages[1] = context.getResources().getDrawable(R.drawable.clubba_walk_down2);
            mClubbaStandDownImage = context.getResources().getDrawable(R.drawable.clubba_walk_down2); 
            
            // load background image as a Bitmap instead of a Drawable b/c
            // we don't need to transform it and it's faster to draw this way
            mBackgroundImage = BitmapFactory.decodeResource(res,
                    R.drawable.earthrise);

//            // Use the regular player image as the model size for all sprites
//            mClubbaWidth = mClubbaStandDownImage.getIntrinsicWidth();
//            mClubbaHalfWidth = mClubbaWidth / 2;
//            mClubbaHeight = mClubbaStandDownImage.getIntrinsicHeight();
//            mClubbaHalfHeight = mClubbaHeight / 2;

            // Initialize paints for speedometer
            mLinePaint = new Paint();
            mLinePaint.setAntiAlias(true);
            mLinePaint.setARGB(255, 0, 255, 0);

            mLinePaintBad = new Paint();
            mLinePaintBad.setAntiAlias(true);
            mLinePaintBad.setARGB(255, 120, 180, 0);

            mScratchRect = new RectF(0, 0, 0, 0);

            mWinsInARow = 0;
            mDifficulty = DIFFICULTY_MEDIUM;

            // initial show-up of lander (not yet playing)
            mX = mClubbaWidth;
            mY = mClubbaHeight * 2;
            mPlayerSpeed = 2;
            mFramesPerTileX = mClubbaWidth / mPlayerSpeed;
            mFramesPerTileY = mClubbaHeight / mPlayerSpeed;
            mHealth = PHYS_FUEL_INIT;
            mEngineFiring = true;
        }

        /**
         * Starts the game, setting parameters for the current difficulty.
         */
        public void doStart() {
            synchronized (mSurfaceHolder) {
                // First set the game for Medium difficulty
                mHealth = PHYS_FUEL_INIT;
                mEngineFiring = false;
                mGoalWidth = (int) (mClubbaWidth * TARGET_WIDTH);
                mGoalSpeed = TARGET_SPEED;
                mGoalAngle = TARGET_ANGLE;
                int speedInit = PHYS_SPEED_INIT;

                // Adjust difficulty params for EASY/HARD
                if (mDifficulty == DIFFICULTY_EASY) {
                    mHealth = mHealth * 3 / 2;
                    mGoalWidth = mGoalWidth * 4 / 3;
                    mGoalSpeed = mGoalSpeed * 3 / 2;
                    mGoalAngle = mGoalAngle * 4 / 3;
                    speedInit = speedInit * 3 / 4;
                } else if (mDifficulty == DIFFICULTY_HARD) {
                    mHealth = mHealth * 7 / 8;
                    mGoalWidth = mGoalWidth * 3 / 4;
                    mGoalSpeed = mGoalSpeed * 7 / 8;
                    speedInit = speedInit * 4 / 3;
                }

                // pick a convenient initial location for the player sprite
                mX = (5 * mClubbaWidth) + mClubbaHalfWidth;
                mY = (5 * mClubbaHeight) + mClubbaHalfHeight;

                // Figure initial spot for landing, not too near center
                while (true) {
                    mGoalX = (int) (Math.random() * (mCanvasWidth - mGoalWidth));
                    if (Math.abs(mGoalX - (mX - mClubbaWidth / 2)) > mCanvasHeight / 6)
                        break;
                }

                mLastTime = System.currentTimeMillis() + 100;
                setState(STATE_RUNNING);
            }
        }

        /**
         * Pauses the physics update & animation.
         */
        public void pause() {
            synchronized (mSurfaceHolder) {
                if (mMode == STATE_RUNNING) setState(STATE_PAUSE);
            }
        }

        /**
         * Restores game state from the indicated Bundle. Typically called when
         * the Activity is being restored after having been previously
         * destroyed.
         * 
         * @param savedState Bundle containing the game state
         */
        public synchronized void restoreState(Bundle savedState) {
            synchronized (mSurfaceHolder) {
                setState(STATE_PAUSE);
                mEngineFiring = false;

                mDifficulty = savedState.getInt(KEY_DIFFICULTY);
                mX = savedState.getInt(KEY_X);
                mY = savedState.getInt(KEY_Y);
                
//                mClubbaWidth = savedState.getInt(KEY_LANDER_WIDTH);
//                mClubbaHeight = savedState.getInt(KEY_LANDER_HEIGHT);
                mGoalX = savedState.getInt(KEY_GOAL_X);
                mGoalSpeed = savedState.getInt(KEY_GOAL_SPEED);
                mGoalAngle = savedState.getInt(KEY_GOAL_ANGLE);
                mGoalWidth = savedState.getInt(KEY_GOAL_WIDTH);
                mWinsInARow = savedState.getInt(KEY_WINS);
                mHealth = savedState.getInt(KEY_FUEL);
            }
        }

        @Override
        public void run() {
            while (mRun) {
                Canvas c = null;
                try {
                    c = mSurfaceHolder.lockCanvas(null);
                    synchronized (mSurfaceHolder) {
                        if (mMode == STATE_RUNNING) updatePhysics();
                        doDraw(c);
                    }
                } finally {
                    // do this in a finally so that if an exception is thrown
                    // during the above, we don't leave the Surface in an
                    // inconsistent state
                    if (c != null) {
                        mSurfaceHolder.unlockCanvasAndPost(c);
                    }
                }
            }
        }

        /**
         * Dump game state to the provided Bundle. Typically called when the
         * Activity is being suspended.
         * 
         * @return Bundle with this view's state
         */
        public Bundle saveState(Bundle map) {
            synchronized (mSurfaceHolder) {
                if (map != null) {
                    map.putInt(KEY_DIFFICULTY, Integer.valueOf(mDifficulty));
                    map.putInt(KEY_X, Integer.valueOf(mX));
                    map.putInt(KEY_Y, Integer.valueOf(mY));
                    map.putInt(KEY_LANDER_WIDTH, Integer.valueOf(mClubbaWidth));
                    map.putInt(KEY_LANDER_HEIGHT, Integer
                            .valueOf(mClubbaHeight));
                    map.putInt(KEY_GOAL_X, Integer.valueOf(mGoalX));
                    map.putInt(KEY_GOAL_SPEED, Integer.valueOf(mGoalSpeed));
                    map.putInt(KEY_GOAL_ANGLE, Integer.valueOf(mGoalAngle));
                    map.putInt(KEY_GOAL_WIDTH, Integer.valueOf(mGoalWidth));
                    map.putInt(KEY_WINS, Integer.valueOf(mWinsInARow));
                    map.putDouble(KEY_FUEL, Double.valueOf(mHealth));
                }
            }
            return map;
        }

        /**
         * Sets the current difficulty.
         * 
         * @param difficulty
         */
        public void setDifficulty(int difficulty) {
            synchronized (mSurfaceHolder) {
                mDifficulty = difficulty;
            }
        }

        /**
         * Sets if the player is currently firing.
         */
        public void setFiring(boolean firing) {
            synchronized (mSurfaceHolder) {
                mEngineFiring = firing;
            }
        }
        
        /**
         * Sets if the player is currently walking.
         */
        public void setWalking(boolean walking) {
            synchronized (mSurfaceHolder) {
                mWalking = walking;
            }
        }
        
        /**
         * Sets if the player should continue motion after stopping.
         */
        public void setPlayerNextDirection(int nextDirection) {
        	synchronized (mSurfaceHolder) {
                mNextDirection = nextDirection;
            }
        }
        
        /**
         * Sets the player's facing direction.
         */
        public void setPlayerDirection(int direction) {
            synchronized (mSurfaceHolder) {
                mPlayerDirection = direction;
            }
        }

        /**
         * Used to signal the thread whether it should be running or not.
         * Passing true allows the thread to run; passing false will shut it
         * down if it's already running. Calling start() after this was most
         * recently called with false will result in an immediate shutdown.
         * 
         * @param b true to run, false to shut down
         */
        public void setRunning(boolean b) {
            mRun = b;
        }

        /**
         * Sets the game mode. That is, whether we are running, paused, in the
         * failure state, in the victory state, etc.
         * 
         * @see #setState(int, CharSequence)
         * @param mode one of the STATE_* constants
         */
        public void setState(int mode) {
            synchronized (mSurfaceHolder) {
                setState(mode, null);
            }
        }

        /**
         * Sets the game mode. That is, whether we are running, paused, in the
         * failure state, in the victory state, etc.
         * 
         * @param mode one of the STATE_* constants
         * @param message string to add to screen or null
         */
        public void setState(int mode, CharSequence message) {
            /*
             * This method optionally can cause a text message to be displayed
             * to the user when the mode changes. Since the View that actually
             * renders that text is part of the main View hierarchy and not
             * owned by this thread, we can't touch the state of that View.
             * Instead we use a Message + Handler to relay commands to the main
             * thread, which updates the user-text View.
             */
            synchronized (mSurfaceHolder) {
                mMode = mode;

                if (mMode == STATE_RUNNING) {
                    Message msg = mHandler.obtainMessage();
                    Bundle b = new Bundle();
                    b.putString("text", "");
                    b.putInt("viz", View.INVISIBLE);
                    msg.setData(b);
                    mHandler.sendMessage(msg);
                } else {
                    mEngineFiring = false;
                    Resources res = mContext.getResources();
                    CharSequence str = "";
                    if (mMode == STATE_READY)
                        str = res.getText(R.string.mode_ready);
                    else if (mMode == STATE_PAUSE)
                        str = res.getText(R.string.mode_pause);
                    else if (mMode == STATE_LOSE)
                        str = res.getText(R.string.mode_lose);
                    else if (mMode == STATE_WIN)
                        str = res.getString(R.string.mode_win_prefix)
                                + mWinsInARow + " "
                                + res.getString(R.string.mode_win_suffix);

                    if (message != null) {
                        str = message + "\n" + str;
                    }

                    if (mMode == STATE_LOSE) mWinsInARow = 0;

                    Message msg = mHandler.obtainMessage();
                    Bundle b = new Bundle();
                    b.putString("text", str.toString());
                    b.putInt("viz", View.VISIBLE);
                    msg.setData(b);
                    mHandler.sendMessage(msg);
                }
            }
        }

        /* Callback invoked when the surface dimensions change. */
        public void setSurfaceSize(int width, int height) {
            // synchronized to make sure these all change atomically
            synchronized (mSurfaceHolder) {
                mCanvasWidth = width;
                mCanvasHeight = height;

                // don't forget to resize the background image
                mBackgroundImage = mBackgroundImage.createScaledBitmap(
                        mBackgroundImage, width, height, true);
            }
        }

        /**
         * Resumes from a pause.
         */
        public void unpause() {
            // Move the real time clock up to now
            synchronized (mSurfaceHolder) {
                mLastTime = System.currentTimeMillis() + 100;
            }
            setState(STATE_RUNNING);
        }

        /**
         * Handles a key-down event.
         * 
         * @param keyCode the key that was pressed
         * @param msg the original event object
         * @return true
         */
        boolean doKeyDown(int keyCode, KeyEvent msg) {
            synchronized (mSurfaceHolder) {
                boolean okStart = false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) okStart = true;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) okStart = true;
                if (keyCode == KeyEvent.KEYCODE_S) okStart = true;

                if (okStart
                        && (mMode == STATE_READY || mMode == STATE_LOSE || mMode == STATE_WIN)) {
                    // ready-to-start -> start
                    doStart();
                    return true;
                } else if (mMode == STATE_PAUSE && okStart) {
                    // paused -> running
                    unpause();
                    return true;
                } else if (mMode == STATE_RUNNING) {
                	// center/space -> fire
                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                            || keyCode == KeyEvent.KEYCODE_SPACE) {
                        setFiring(true);
                        return true;
                        // left/a -> left
                    } else if (!mWalking) {
                    	if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                    			|| keyCode == KeyEvent.KEYCODE_A) {
	                        setPlayerDirection(DIRECTION_LEFT);
	                        setWalking(true);
	                        return true;
	                        // right/s -> right
	                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
	                            || keyCode == KeyEvent.KEYCODE_S) {
	                    	setPlayerDirection(DIRECTION_RIGHT);
	                    	setWalking(true);
	                        return true;
	                        // up -> pause
	                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP
	                    		|| keyCode == KeyEvent.KEYCODE_W) {
	                    	setPlayerDirection(DIRECTION_UP);
	                    	setWalking(true);
	                        return true;
	                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN
	                    		|| keyCode == KeyEvent.KEYCODE_Z) {
	                    	setPlayerDirection(DIRECTION_DOWN);
	                    	setWalking(true);
	                        return true;
	                    }
                    }
                }

                return false;
            }
        }

        /**
         * Handles a key-up event.
         * 
         * @param keyCode the key that was pressed
         * @param msg the original event object
         * @return true if the key was handled and consumed, or else false
         */
        boolean doKeyUp(int keyCode, KeyEvent msg) {
            boolean handled = false;

            synchronized (mSurfaceHolder) {
                if (mMode == STATE_RUNNING) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                            || keyCode == KeyEvent.KEYCODE_SPACE) {
                        setFiring(false);
                        handled = true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                            || keyCode == KeyEvent.KEYCODE_A
                            || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                            || keyCode == KeyEvent.KEYCODE_S
                            || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                            || keyCode == KeyEvent.KEYCODE_Z
                            || keyCode == KeyEvent.KEYCODE_DPAD_UP
                            || keyCode == KeyEvent.KEYCODE_W) {
                    	setWalking(false);
                        handled = true;
                    }
                }
            }

            return handled;
        }
        
        boolean doTrackballEvent(MotionEvent event) {
    		synchronized (mSurfaceHolder) {
    			float x = event.getX();
	    		float y = event.getY();
	    		boolean okStart = false;
                boolean isHorizontal = ( Math.abs(x) > Math.abs(y) );
	    		if (!isHorizontal) {
	    			okStart = true;
	    		}
	    		
                if (okStart
                        && (mMode == STATE_READY || mMode == STATE_LOSE || mMode == STATE_WIN)) {
                    // ready-to-start -> start
                    doStart();
                    return true;
                } else if (mMode == STATE_PAUSE && okStart) {
                    // paused -> running
                    unpause();
                    return true;
                } else if (mMode == STATE_RUNNING) {
                	if (isHorizontal && (!mWalking || mCurrentFrame >= mFramesPerTileX - mFramesAllowInput)) {
    	        		if (x > 0) {
    	        			setPlayerNextDirection(DIRECTION_RIGHT);
    	        		} else if (x < 0) {
    	        			setPlayerNextDirection(DIRECTION_LEFT);
    	        		}
    	    		} else if (!mWalking || mCurrentFrame >= mFramesPerTileY - mFramesAllowInput) {
    	    			if (y > 0) {
    	    				setPlayerNextDirection(DIRECTION_DOWN);
    	    			} else {
    	    				setPlayerNextDirection(DIRECTION_UP);
    	    			}
    	    		}
                }
    		}
        	return true;
        }

        /**
         * Draws the ship, fuel/speed bars, and background to the provided
         * Canvas.
         */
        private void doDraw(Canvas canvas) {
            // Draw the background image. Operations on the Canvas accumulate
            // so this is like clearing the screen.
            canvas.drawBitmap(mBackgroundImage, 0, 0, null);

            int yTop = mCanvasHeight - (mY + mClubbaHeight / 2);
            int xLeft = mX - mClubbaWidth / 2;

            // Draw the fuel gauge
            int fuelWidth = (int) (UI_BAR * mHealth / PHYS_FUEL_MAX);
            mScratchRect.set(4, 4, 4 + fuelWidth, 4 + UI_BAR_HEIGHT);
            canvas.drawRect(mScratchRect, mLinePaint);

            // Draw the landing pad
            canvas.drawLine(mGoalX, 1 + mCanvasHeight - TARGET_PAD_HEIGHT,
                    mGoalX + mGoalWidth, 1 + mCanvasHeight - TARGET_PAD_HEIGHT,
                    mLinePaint);


            // Draw the ship with its current rotation
            canvas.save();
            if (mMode == STATE_LOSE) {
                mCrashedImage.setBounds(xLeft, yTop, xLeft + mClubbaWidth, yTop
                        + mClubbaHeight);
                mCrashedImage.draw(canvas);
            } else if (mEngineFiring) {
                mFiringImage.setBounds(xLeft, yTop, xLeft + mClubbaWidth, yTop
                        + mClubbaHeight);
                mFiringImage.draw(canvas);
            } else {
            	// FIXME: Fix direction
                mClubbaStandDownImage.setBounds(xLeft, yTop, xLeft + mClubbaWidth, yTop
                        + mClubbaHeight);
                mClubbaStandDownImage.draw(canvas);
            }
            canvas.restore();
        }

        /**
         * Figures the lander state (x, y, fuel, ...) based on the passage of
         * realtime. Does not invalidate(). Called at the start of draw().
         * Detects the end-of-game and sets the UI to the next state.
         */
        private void updatePhysics() {
            long now = System.currentTimeMillis();

            // Do nothing if mLastTime is in the future.
            // This allows the game-start to delay the start of the physics
            // by 100ms or whatever.
            if (mLastTime > now) return;

            double elapsed = (now - mLastTime) / 1000.0;

            if (mEngineFiring) {
                // taking 0 as up, 90 as to the right
                // cos(deg) is ddy component, sin(deg) is ddx component
                double elapsedFiring = elapsed;
                double fuelUsed = elapsedFiring * PHYS_FUEL_SEC;

                // tricky case where we run out of fuel partway through the
                // elapsed
                if (fuelUsed > mHealth) {
                    elapsedFiring = mHealth / fuelUsed * elapsed;
                    fuelUsed = mHealth;

                    // Oddball case where we adjust the "control" from here
                    mEngineFiring = false;
                }

                mHealth -= fuelUsed;

            }
            
            // Try to proceed walking if player is in the middle of walking.
            // TODO: This involves updating graphics
            if (mWalking) {
            	int newX, newY;
            	switch (mPlayerDirection) {
            	case DIRECTION_LEFT:
            		// TODO: in addition to screen borders, also check solid tiles
            		newX = mX - mPlayerSpeed;
            		if (newX <= (mClubbaHalfWidth)) {
            			mCurrentFrame = 0;
            		} else {
            			// Increment motion frame. (Animation frame is different.)
                    	incrementFrame();
                    	mX = newX;
            		}
            		break;
            	case DIRECTION_RIGHT:
            		newX = mX + mPlayerSpeed;
            		if (newX >= (480 - mClubbaHalfWidth)) {
            			mCurrentFrame = 0;
            		} else {
            			incrementFrame();
                    	mX = newX;
            		}
            		break;
            	case DIRECTION_DOWN:
            		newY = mY - mPlayerSpeed;
            		if (newY <= (mClubbaHalfHeight)) {
            			mCurrentFrame = 0;
            		} else {
            			incrementFrame();
                    	mY = newY;
            		}
            		break;
            	case DIRECTION_UP:
            		newY = mY + mPlayerSpeed;
            		if (newY >= (320 - mClubbaHalfHeight)) {
            			mCurrentFrame = 0;
            		} else {
            			incrementFrame();
                    	mY = newY;
            		}
            		break;
            	}
            	
            	if (mCurrentFrame == 0) {
            		endWalk();
            	}
            } else {
            	beginWalk();
            }
            

            mLastTime = now;

//            // Evaluate if we have landed ... stop the game
//            int yLowerBound = mClubbaHeight >> 1;
//            int yUpperBound = 320 - mClubbaHeight >> 1;
//            if (mY <= yLowerBound) {
//                mY = yLowerBound;
//
//                int result = STATE_LOSE;
//                CharSequence message = "";
//                Resources res = mContext.getResources();
//                boolean onGoal = (mGoalX <= mX - mClubbaWidth / 2 && mX
//                        + mClubbaWidth / 2 <= mGoalX + mGoalWidth);
//
////                // "Hyperspace" win -- upside down, going fast,
////                // puts you back at the top.
////                if (onGoal && Math.abs(mHeading - 180) < mGoalAngle
////                        && speed > PHYS_SPEED_HYPERSPACE) {
////                    result = STATE_WIN;
////                    mWinsInARow++;
////                    doStart();
////
////                    return;
////                    // Oddball case: this case does a return, all other cases
////                    // fall through to setMode() below.
////                } else if (!onGoal) {
////                    message = res.getText(R.string.message_off_pad);
////                } else {
////                    result = STATE_WIN;
////                    mWinsInARow++;
////                }
////
//                setState(result, message);
//            }
        }
    }

    /** Handle to the application context, used to e.g. fetch Drawables. */
    private Context mContext;

    /** Pointer to the text view to display "Paused.." etc. */
    private TextView mStatusText;

    /** The thread that actually draws the animation */
    private RPGThread thread;

    public RPGView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // register our interest in hearing about changes to our surface
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);

        // create thread only; it's started in surfaceCreated()
        thread = new RPGThread(holder, context, new Handler() {
            @Override
            public void handleMessage(Message m) {
                mStatusText.setVisibility(m.getData().getInt("viz"));
                mStatusText.setText(m.getData().getString("text"));
            }
        });

        setFocusable(true); // make sure we get key events
    }

    /**
     * Fetches the animation thread corresponding to this LunarView.
     * 
     * @return the animation thread
     */
    public RPGThread getThread() {
        return thread;
    }

    /**
     * Standard override to get key-press events.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent msg) {
        return thread.doKeyDown(keyCode, msg);
    }

    /**
     * Standard override for key-up.
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent msg) {
        return thread.doKeyUp(keyCode, msg);
    }
    
    @Override
    public boolean onTrackballEvent(MotionEvent event) {
    	return thread.doTrackballEvent(event);
    }

    /**
     * Standard window-focus override. Notice focus lost so we can pause on
     * focus lost. e.g. user switches to take a call.
     */
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (!hasWindowFocus) thread.pause();
    }

    /**
     * Installs a pointer to the text view used for messages.
     */
    public void setTextView(TextView textView) {
        mStatusText = textView;
    }

    /* Callback invoked when the surface dimensions change. */
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
        thread.setSurfaceSize(width, height);
    }

    /*
     * Callback invoked when the Surface has been created and is ready to be
     * used.
     */
    public void surfaceCreated(SurfaceHolder holder) {
        // start the thread here so that we don't busy-wait in run()
        // waiting for the surface to be created
        thread.setRunning(true);
        thread.start();
    }

    /*
     * Callback invoked when the Surface has been destroyed and must no longer
     * be touched. WARNING: after this method returns, the Surface/Canvas must
     * never be touched again!
     */
    public void surfaceDestroyed(SurfaceHolder holder) {
        // we have to tell thread to shut down & wait for it to finish, or else
        // it might touch the Surface after we return and explode
        boolean retry = true;
        thread.setRunning(false);
        while (retry) {
            try {
                thread.join();
                retry = false;
            } catch (InterruptedException e) {
            }
        }
    }
}
