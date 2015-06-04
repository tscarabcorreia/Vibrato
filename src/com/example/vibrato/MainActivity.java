package com.example.vibrato;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.BarLineScatterCandleDataSet;
import com.github.mikephil.charting.data.DataSet;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.FillFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.UniversalAudioInputStream;
import be.tarsos.dsp.io.android.AndroidAudioInputStream;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

public class MainActivity extends Activity {

	private LineGraphSeries<DataPoint> series;
	private ArrayList<Entry> entries = new ArrayList<Entry>();
	private Thread listenThread = null;
	private int lastX = 0;
	private ArrayList<String> xVals = new ArrayList<String>();
	private PitchDetectionHandler pitchDetected;
	private LineChart chart;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		setButtonHandlers();
		enableButtons(false);

		LinearLayout graphLayout = (LinearLayout) findViewById(R.id.graph);

		GraphView graph = new GraphView(this);
		// data
		series = new LineGraphSeries<DataPoint>();
		graph.addSeries(series);
		// customize a little bit viewport
		Viewport viewport = graph.getViewport();
		viewport.setScalable(true);
		viewport.setScrollable(true);
		viewport.setXAxisBoundsManual(true);
		viewport.setMinY(-1);
		viewport.setMinX(0);
		viewport.setMaxX(1000);
		//graphLayout.addView(graph);
		chart = new LineChart(this);
		graphLayout.addView(chart);
	}

	private void setButtonHandlers() {
		((Button) findViewById(R.id.btnStart)).setOnClickListener(btnClick);
		((Button) findViewById(R.id.btnStop)).setOnClickListener(btnClick);
	}

	private void enableButton(int id, boolean isEnable) {
		((Button) findViewById(id)).setEnabled(isEnable);
	}

	private void enableButtons(boolean isRecording) {
		enableButton(R.id.btnStart, !isRecording);
		enableButton(R.id.btnStop, isRecording);
	}

	private View.OnClickListener btnClick = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			switch (v.getId()) {
				case R.id.btnStart: {
					enableButtons(true);
					AudioFileRecorder.startRecording();
					break;
				}
				case R.id.btnStop: {
					AudioFileRecorder.stopRecording();
	
					UniversalAudioInputStream aais = new UniversalAudioInputStream(
							AudioFileRecorder.getAudioRecorded(), new TarsosDSPAudioFormat(AudioFileRecorder.RECORDER_SAMPLERATE, 16, 1, false, false));
					processAudioShowingGraph(new AudioDispatcher(aais, 1024, 512));
					enableButtons(false);
					break;
				}
			}
		}
	};

	public void processAudioShowingGraph(AudioDispatcher dispatcher) {
		pitchDetected= new PitchDetectionHandler() {
			@Override
			public void handlePitch(
					final PitchDetectionResult pitchDetectionResult,
					AudioEvent audioEvent) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						float pitch = pitchDetectionResult.getPitch();
						series.appendData(
								new DataPoint(lastX++, pitch), true,
								1000);
						entries.add(new Entry(pitch, lastX));
						xVals.add((lastX) + "");
					}
				});
			}
		};
		dispatcher.addAudioProcessor(new PitchProcessor(PitchEstimationAlgorithm.AMDF, 22050, 1024, pitchDetected){
			@Override
			public void processingFinished() {
				LineDataSet chartDataSet = new LineDataSet(entries, "Pitch");
				ArrayList<LineDataSet> dataSets = new ArrayList<LineDataSet>();
		        dataSets.add(chartDataSet);
		        LineData data = new LineData(xVals, dataSets);
		        chart.setData(data);
		        chart.getAxisRight().setEnabled(false);
		        chart.setDragEnabled(true);
		        chart.setScaleEnabled(true);
			}
		});

		listenThread = new Thread(dispatcher, "Audio Dispatcher");
		listenThread.start();
	}

	public void onlinePitchDetection() {
		AudioDispatcher dispatcher = AudioDispatcherFactory
				.fromDefaultMicrophone(22050, 1024, 0);
		processAudioShowingGraph(dispatcher);
	}
}