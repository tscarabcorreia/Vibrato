package com.vibrato;

import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;

import com.vibrato.R;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.UniversalAudioInputStream;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.TransitionDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.LinearLayout;

public class MainActivity extends Activity {

	private ArrayList<Entry> entries = new ArrayList<Entry>();
	private Thread listenThread = null;
	private int lastX = 0;
	private ArrayList<String> xVals = new ArrayList<String>();
	private PitchDetectionHandler pitchDetected;
	private LineChart chart;
	private LineChart chart2;
	private ProgressDialog progressDialog;
	private Chronometer clock;
	private long timeElapsed;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		setButtonHandlers();
		initializeGraph();
		clock = (Chronometer) findViewById(R.id.chronometer);
		progressDialog = new ProgressDialog(this);
	}

	private void initializeGraph() {
		LinearLayout graphLayout = (LinearLayout) findViewById(R.id.graph);
		chart = new LineChart(this);
		graphLayout.addView(chart);
        chart.getAxisRight().setEnabled(false);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.getAxisLeft().setStartAtZero(false);
        chart.setDescription("F0 time serie");
		LinearLayout graphLayout2 = (LinearLayout) findViewById(R.id.graph2);
		chart2 = new LineChart(this);
		graphLayout2.addView(chart2);
        chart2.getAxisRight().setEnabled(false);
        chart2.setDragEnabled(true);
        chart2.setScaleEnabled(true);
        chart2.getAxisLeft().setStartAtZero(true);
        chart2.setDescription("DFT");
	}

	private void setButtonHandlers() {
		((ImageButton) findViewById(R.id.btnStart)).setOnTouchListener(btnClick);
	}

	private View.OnTouchListener btnClick = new View.OnTouchListener() {

		@Override
		public boolean onTouch(View v, MotionEvent event) {
	        switch (event.getAction()) {
	        case MotionEvent.ACTION_DOWN:
	        	TransitionDrawable transition = (TransitionDrawable) v.getBackground();
	        	transition.startTransition(700);
        		Animation animScale = AnimationUtils.loadAnimation(v.getContext(), R.anim.button_scale_animation);
	        	v.startAnimation(animScale);
				timeElapsed = 0;
				clock.setBase(SystemClock.elapsedRealtime());
				clock.start();
				AudioFileRecorder.startRecording();
	            break;
	        case MotionEvent.ACTION_UP:
	        	Animation animScaleBack = AnimationUtils.loadAnimation(v.getContext(), R.anim.button_scale_animation_reverse);
	        	v.startAnimation(animScaleBack);
	        	TransitionDrawable transitionback = (TransitionDrawable) v.getBackground();
	        	transitionback.reverseTransition(300);
	        	AudioFileRecorder.stopRecording();
				timeElapsed = SystemClock.elapsedRealtime() - clock.getBase();
				clock.stop();
				if (timeElapsed < 2*1000){
					showAlert(v.getContext(), getString(R.string.short_audio_recorded), getString(R.string.short_audio_recorded_instruction));
				}
				else{
					processAudio(AudioFileRecorder.getAudioRecorded());
				}
	            break;
	        case MotionEvent.ACTION_MOVE:
	 
	            break;
	        }
	        return true;
	    }
	};


	private void showAlert(final Context c, final String title, final String message) {
		runOnUiThread(new Runnable() {
			
			@Override
			public void run() {
				AlertDialog.Builder builder = new AlertDialog.Builder(c);
				builder.setTitle(title)
						.setMessage(message)
						.setCancelable(false)
						.setPositiveButton(R.string.ok, null);
				AlertDialog alert = builder.create();
				alert.show();
			}
		});
	}
	
	private void processAudioShowingGraph(AudioDispatcher dispatcher, final Context c) {
		progressDialog.setIndeterminate(false);
		progressDialog.setMax((int) (timeElapsed*1.2));
		progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		progressDialog.setMessage(c.getString(R.string.processing_message));
		progressDialog.show();
		progressDialog.setProgress((int) (timeElapsed*0.1));
		entries.clear();
		xVals.clear();
		lastX = 0;
		pitchDetected = new PitchDetectionHandler() {
			@Override
			public void handlePitch(final PitchDetectionResult pitchDetectionResult,AudioEvent audioEvent) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						float pitch = pitchDetectionResult.getPitch();
						entries.add(new Entry(pitch, lastX++));
						xVals.add(new DecimalFormat("#.## s").format(lastX * AudioFileRecorder.WINDOW_SIZE));
						progressDialog.incrementProgressBy((int) (AudioFileRecorder.WINDOW_SIZE*1000));
					}
				});
			}
		};
		dispatcher.addAudioProcessor(new PitchProcessor(PitchEstimationAlgorithm.AMDF, AudioFileRecorder.RECORDER_SAMPLERATE, (int) AudioFileRecorder.getBufferSize(), pitchDetected){
			@Override
			public void processingFinished() {
				progressDialog.incrementProgressBy((int) (timeElapsed*0.1));
				entries = F0Spec.PostProcessing(entries);
				ArrayList<LineDataSet> dataSets = new ArrayList<LineDataSet>();
				LineDataSet pitchDataSet = new LineDataSet(entries, getString(R.string.pitch_dataset_name));
				pitchDataSet.setLabel("F0 (Hz)");
				pitchDataSet.setColor(Color.BLUE);
				pitchDataSet.setCircleColor(Color.BLUE);
				pitchDataSet.setLineWidth(1f);
				pitchDataSet.setCircleSize(1f);
				pitchDataSet.setDrawCircleHole(false);
				pitchDataSet.setValueTextSize(9f);
				pitchDataSet.setFillAlpha(65);
				pitchDataSet.setFillColor(Color.BLACK);
		        dataSets.add(pitchDataSet);
				
				ArrayList<Entry> validWindow = F0Spec.getValidWindow(entries, 100);
				ArrayList<Entry> validWindowFiltered = null;
				if (validWindow == null){
					showAlert(c, getString(R.string.poor_audio_quality), getString(R.string.poor_audio_quality_instruction));
				}
				else{
			        LineDataSet slicedDataSet = new LineDataSet(validWindow, getString(R.string.valid_dataset_name));
			        slicedDataSet.setColor(Color.RED);
			        slicedDataSet.setCircleColor(Color.RED);
			        slicedDataSet.setLineWidth(1f);
			        slicedDataSet.setCircleSize(1f);
			        slicedDataSet.setDrawCircleHole(false);
			        slicedDataSet.setValueTextSize(9f);
			        slicedDataSet.setFillAlpha(65);
			        slicedDataSet.setFillColor(Color.BLACK);
			        dataSets.add(slicedDataSet);
			        
			        validWindowFiltered = F0Spec.RemoveDC(validWindow);
			        LineDataSet filteredDataSet = new LineDataSet(validWindowFiltered, getString(R.string.filtered_dataset_name));
			        filteredDataSet.setColor(Color.GREEN);
			        filteredDataSet.setCircleColor(Color.GREEN);
			        filteredDataSet.setLineWidth(1f);
			        filteredDataSet.setCircleSize(1f);
			        filteredDataSet.setDrawCircleHole(false);
			        filteredDataSet.setValueTextSize(9f);
			        filteredDataSet.setFillAlpha(65);
			        filteredDataSet.setFillColor(Color.BLACK);
			        dataSets.add(filteredDataSet);
				}

		        LineData data = new LineData(xVals, dataSets);
		        chart.setData(data);
		        runOnUiThread(new Runnable() {
					
					@Override
					public void run() {
						((LinearLayout) findViewById(R.id.graph)).setVisibility(View.VISIBLE);
						progressDialog.dismiss();
				        chart.animateY(2500, Easing.EasingOption.EaseInOutQuart);
						chart.invalidate();
					}
				});
		        if (validWindowFiltered != null)
		        {
		        	float[] dft = F0Spec.GetPercentualExtent(validWindow);
			        int i = 0;
			    	ArrayList<Entry> dftentries = new ArrayList<Entry>();
			    	ArrayList<String> dftXVals = new ArrayList<String>();
			        for (float n : dft)
			        {
						dftentries.add(new Entry(n, i));
						dftXVals.add(new DecimalFormat("#.# Hz").format(i * 0.1));
						i++;
			        }
					ArrayList<LineDataSet> dftDataSets = new ArrayList<LineDataSet>();
					LineDataSet dftDataSet = new LineDataSet(dftentries, "Extent");
					dftDataSet.setLabel("Percentual Extent (%Hz)");
					dftDataSet.setColor(Color.BLUE);
					dftDataSet.setCircleColor(Color.BLUE);
					dftDataSet.setLineWidth(1f);
					dftDataSet.setCircleSize(1f);
					dftDataSet.setDrawCircleHole(false);
					dftDataSet.setValueTextSize(9f);
					dftDataSet.setFillAlpha(65);
					dftDataSet.setFillColor(Color.BLACK);
					dftDataSets.add(dftDataSet);
					LineData data2 = new LineData(dftXVals, dftDataSets);
			        chart2.setData(data2);
			        runOnUiThread(new Runnable() {
						
						@Override
						public void run() {
							((LinearLayout) findViewById(R.id.graph2)).setVisibility(View.VISIBLE);
					        chart2.animateY(2500, Easing.EasingOption.EaseInOutQuart);
							chart2.invalidate();
						}
					});
		        }
			}
		});

		listenThread = new Thread(dispatcher, "Audio Dispatcher");
		listenThread.start();
	}

	@SuppressWarnings("unused")
	private void onlinePitchDetection() {
		AudioDispatcher dispatcher = AudioDispatcherFactory
				.fromDefaultMicrophone(22050, 4410, 0);
		processAudioShowingGraph(dispatcher, this);
	}

	private void processAudio(InputStream inputStream) {
		UniversalAudioInputStream aais = new UniversalAudioInputStream(inputStream, 
				new TarsosDSPAudioFormat(AudioFileRecorder.RECORDER_SAMPLERATE, 16, 1, false, false));
		processAudioShowingGraph(new AudioDispatcher(aais, (int) AudioFileRecorder.getBufferSize(), (int) AudioFileRecorder.getBufferSize()/2), this);
	}
}