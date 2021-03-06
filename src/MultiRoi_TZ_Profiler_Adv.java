//package ij.plugin;
/// TODO: pack this up into a single file:     # jar cvf MultiRoi_TZ_Profiler.jar *.class *.java
import ij.plugin.*;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.*; //for RoiManager
import ij.util.Tools;
//import java.awt.*;

import ij.io.*; // for OpenDialog
import java.io.*; //for BufferedReader
import java.util.*; //for StringTokenizer


///
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.*;
//import java.util.*;
//import java.util.Properties;
import java.io.*;
import java.lang.*;
import java.lang.reflect.*;
import java.math.*;
import javax.swing.*;
import javax.swing.event.*;
//import javax.swing.border.*;
import javax.swing.text.*;

import java.net.URI; // to open default browser to load plugin update link
import java.net.URISyntaxException; // to open default browser to load plugin update link

//import ij.*;
//import ij.plugin.filter.PlugInFilter;
//import ij.plugin.PlugIn;
//import ij.gui.*;
//import ij.process.*;
//import ij.measure.*;
//import ij.io.*;
import ij.gui.NonBlockingGenericDialog ;

// This plugin modifies ZAxisProfiler.java code from NIH (included with ImageJ)
// to add the capability to display multiple traces from multiple ROIs (from the ROIManager
// and to overlay the electrical stimulation trace to the z axis profile plotter
// displays a scaled trace for each of the ROIs added to the ROI manager
// Iaroslav Savtchouk (Volterra lab) IAS 2015-10-27 iarsav@hotmail.com
// version changes
// 2017-09-26 Renamed to MultiRoi_TZ_Profiler and refactored to code into a publicly accessible version
/// 4-13 changes for Barbara to read in columns with a different tab separator
/// 2018-08-23 forked MultiRoi_TZ_Profiler_Adv.java
///     this will split up the file into subfiles based on the threshold of the channel



/** Implements the Image/Stack/Plot Z-axis Profile command. */
public class MultiRoi_TZ_Profiler_Adv implements PlugIn, Measurements, PlotMaker {
	private static String[] choices = {"time", "z-axis"};
	private static String choice = choices[0];
	private boolean showingDialog;
	private ImagePlus imp;
	private boolean isPlotMaker;
	private boolean timeProfile;
	private boolean firstTime = true;
	RoiManager myRoiManager; //to access saved ROIs
	Roi [] myRoi = new Roi [1000]; //maximum number of ROIs
    int myRoi_count=0;
    float[] pt3d; // pt3d = new float [1000000];
    float[] ptX;
    float[] ptY;
    int i=0;
    int imageTraceLength=0;
    boolean useTXTFileFirstColumnAsTimetag = false;

    float firstTimeTag=0; //reading from the first column of the electrical stimulation file, if any
    float lastTimeTag=100;
    Plot plot;
    PlotMaker myPlotMakerRef;

    traceDisplayParametersContainer params = new traceDisplayParametersContainer ();
    boolean readElTrace=true;
    boolean printDebugMsg =false ; // set to true to print additional details via IJ.log ...    if (printDebugMsg) ...

    //test for now
    int dims_dataset_x_start_pc=0, dims_dataset_x_stop_pc=100; // the size of the dataset to display
    int dims_dataset_x_start=0, dims_dataset_x_stop=0; //absolute counts for the dataset start/stop
    boolean normalizeEachTraceMinToMax=true;

    private PlotControlDialog myPlotControlDialog;
    double ymin =  0.0, ymax=0.0;


	public void run(String arg) {
		imp = IJ.getImage();
		if (imp.getStackSize()<2) {
			IJ.error("ZAxisProfiler", "This command requires a stack.");
			return;
		}

displayControlDialog(true);
if (printDebugMsg) params.printAllVariables() ;


String html = "<html>"
     +"<h1>Stimulation trace display details</h1>"
     +"<font size=+0>"
     +"Current version reads only ascii-formatted text files, "
     +"ignoring the first column except for the first and last values used for x-axis scaling.<br>"
     +"Plotting assumes that both the frames and the electrical traces have a constant, fixed sampling rate."
     +" The start-stop of the electrical trace is in effect stretched over the imaging trace.<br>"
     +"<font color=red>If the sampling intervals vary, you need to re-scale the electrical trace in another program "
     +"or modify the plugin code and recompile.</font> You may also check online for an updated plugin by selecting the checkbox on the file load screen"
     +"</font>"; //     +"<a href=\"https://www.google.com\">Testing link</a>"


NonBlockingGenericDialog myNBGD= new NonBlockingGenericDialog("Continue?");
myNBGD.addMessage ("Would you like to load an electrical stimulation file?\n"
                   + "This file needs to contain tab-separated columns.\n"
                   + "Values from the last column will be plotted. \nClick \"Skip\" to skip loading, or \"Cancel\" to abort the plugin" );
myNBGD.enableYesNoCancel("Load file", "Skip loading");
myNBGD.addHelp(html);
//html="https://wwwfbm.unil.ch/dnf/group/glia-an-active-synaptic-partner/research-technology" ;
//myNBGD.addHelp(html);
myNBGD.addCheckbox("Open webpage to check for updates to this plugin",
                        false);


myNBGD.showDialog();



if (myNBGD.wasCanceled()) {
 displayControlDialog(false);
        return;
} else if (myNBGD.wasOKed()) readElTrace=true;
else readElTrace=false;

if ( myNBGD.getNextBoolean() ) if(Desktop.isDesktopSupported())
{
    String updateURL="https://wwwfbm.unil.ch/dnf/group/glia-an-active-synaptic-partner/research-technology";
  try {
      Desktop.getDesktop().browse(new URI(updateURL));
  }
  catch (IOException e) {
      IJ.log ("Cannot load update URL. Load manually by going to \n"+updateURL);
  }
  catch (URISyntaxException e) {
      IJ.log ("Cannot load update URL. Load manually by going to \n"+updateURL);
  }
}

String stringTokenizerColumnSep = ", ";
		if (readElTrace) {
                useTXTFileFirstColumnAsTimetag=true;
            OpenDialog oF = new OpenDialog("Open ELECTRICAL STIM file", "n", "*.txt");
            String fName = oF.getDirectory() + oF.getFileName();
            String sBuff;
            int txtFileLength = 0;
            int numOfTabColumns = 0;

            if (oF.getPath()!=null) try //if the dialog was not canceled, try to read the file in
             {  BufferedReader brIn = new BufferedReader(new FileReader(fName)); // wrap a BufferedReader around FileReader to speed up the IO
                    // first, read the number of lines in a file, then re-set to the stream beginning, and read them in
                    //brIn.mark(0);
                boolean bOnce = true;
                sBuff = brIn.readLine();
                while (( sBuff = brIn.readLine()) != null)   {
                    txtFileLength++;
                    StringTokenizer myStringTokenizer = new StringTokenizer(sBuff, stringTokenizerColumnSep);
                    if (myStringTokenizer.countTokens()<2) {
                        //IJ.log (" COMMA column separator failed, trying TAB");
                        stringTokenizerColumnSep="\t";
                         myStringTokenizer = new StringTokenizer(sBuff, stringTokenizerColumnSep);

                    }

                    if ( bOnce ) { //
                            //IJ.log ("Text file: located columns: "+  myStringTokenizer.countTokens() + (bOnce=false) );
                        numOfTabColumns=myStringTokenizer.countTokens();
                        bOnce=false ;
                        if (numOfTabColumns>1) firstTimeTag=(float)Double.parseDouble(myStringTokenizer.nextToken());
                    }
                }
                boolean continueLoading=true;
                if (numOfTabColumns<2) {
                    IJ.error ("ERROR: no TAB-separated columns found in the text file specified! \nAt least two tab-separated text number columns are required."
                              +"\nStimulation traces will not be loaded. To try again, re-run the plugin"
                              +"\nselecting a properly formatted file.");
                    //brIn.close();
                    useTXTFileFirstColumnAsTimetag=false;
                    continueLoading=false;
                }
                if (printDebugMsg) IJ.log ("Read in "+ txtFileLength + " lines." );

                //brIn.reset(); //mark-reset doesn't work? wrong usage? let's just re-open...
                brIn.close();
                if (continueLoading) { // if file has at least two tab-separated columns, as determined above...
                    pt3d = new float [txtFileLength*2]; //allow "zoom out" to 200% of trace length

                    brIn = new BufferedReader(new FileReader(fName)); //re-open file stream
                    sBuff = brIn.readLine();
                    while (( sBuff = brIn.readLine()) != null) {
                        StringTokenizer myStringTokenizer = new StringTokenizer(sBuff, stringTokenizerColumnSep);
                        lastTimeTag= (float)Double.parseDouble(myStringTokenizer.nextToken()); //first column contains the time code
                        ///here we assume that the sampling period for the frames is identical and the file starts from 0. we therefore only care when it ends.
                        //for (int c=numOfTabColumns-2; c>=1; c--) pt3d[i]= (float)Double.parseDouble(myStringTokenizer.nextToken()); //discard middle columns
                        int counter1=0;
                        while ( myStringTokenizer.hasMoreElements()  && counter1 <1) { // always read the last column
                            //counter1++; ///UNCOMMENT THIS ONE TO READ SECOND COLUMN
                            pt3d[i]= (float)Double.parseDouble(myStringTokenizer.nextToken());
                        }

                        IJ.showProgress (((float)i+1)/txtFileLength); ///TODO add real size of file for progress bar
                        //IJ.log ("Status "+ ((float)i)/txtFileLength);
                        //IJ.log(" Read in point"+i+" value "+pt3d[i]);
                        i++;
                    }
                    lastTimeTag-=firstTimeTag; // subtract the "start time" of the file, in case it is not zero
                    brIn.close();
                }
             }
             catch(IOException e)
             {  IJ.log("Failed to read electrical stimulation file: " + e);
             useTXTFileFirstColumnAsTimetag=false;
             }
		}

		Roi roi = imp.getRoi();
		isPlotMaker = !IJ.macroRunning();
		plot = getPlot();
		if (plot!=null) {
			if (isPlotMaker) {
				myPlotMakerRef=this;
				plot.setPlotMaker(this);
			}
			plot.show();

		}
	}

	     void showAbout()
     {	IJ.showMessage("About MultiRoi_TZ_Profiler...",
            "This plugin is used to display the profiles of multiple ROIs. \n" +
            "It is the modification of the default ImageJ ZAxisProfiler included with ImageJ. \n" +
            "Modified by Iaroslav Savtchouk/ Volterra lab, 2015-7. \n" +
            "Original code copyright: NIH and/or the respective owners."
	   );
      }

    public void doSplitUpDataSetOverThreshold () {

        /// 1. find the correct location for the split, and add +/- padding frames
        Roi roi = imp.getRoi();
        ImageProcessor ip = imp.getProcessor();
		double minThreshold = ip.getMinThreshold();
		double maxThreshold = ip.getMaxThreshold();

        float[] y;

		boolean hyperstack = imp.isHyperStack();
		if (hyperstack)
			y = getHyperstackProfile(roi, minThreshold, maxThreshold);
		else
			y = getZAxisProfile(roi, minThreshold, maxThreshold);
		if (y==null)
			return ;
		float[] x = new float [y.length];
		for (int z=0; z<y.length; z++) x[z]=z;

        float yMin, yMax, yMean, yStDev, yThreshVal; //for each trace
        yMin=findMin(y);
        yMean=findMean(y);
        yMax=findMax(y);
        yStDev=findStDev(y);
        yThreshVal= yMean + 4.0f*yStDev;

        plot = new Plot("Thresholding", "Frames", "Mean", x, y);
plot.setColor(Color.black);
plot.show();
 		float[] yThresh = new float [y.length];
		for (int z=0; z<y.length; z++) yThresh[z]=yMean;
            plot.setColor(Color.blue);
            plot.addPoints(x, yThresh, Plot.LINE);
            plot.show();

 		float[] yThresh_yStDev = new float [y.length];
		for (int z=0; z<y.length; z++) yThresh_yStDev[z]=yThreshVal; //threshold
            plot.setColor(Color.red);
            plot.addPoints(x, yThresh_yStDev, Plot.LINE);
            plot.show();


            float[] yThresh_splitRange = new float [y.length];
            for (int z=0; z<y.length; z++) yThresh_splitRange[z]=0; //threshold

    int splitPoint =0;
    int splitPoint_Counter =0;
    int peakPaddingFrames_pre  =params.tF_Split_shoulder_Pre.i ; //=20;
    int peakPaddingFrames_post =params.tF_Split_shoulder_Post.i ;//=100;

    while (splitPoint >=0) {
        splitPoint=findArrayIndexExceedingThreshold ( y, yThreshVal, splitPoint+peakPaddingFrames_post);
        if (splitPoint < 0) break;
            for (int z=splitPoint-peakPaddingFrames_pre; z<y.length && z<=splitPoint+peakPaddingFrames_post; z++) {
                    if ( z >splitPoint-peakPaddingFrames_pre ||z < splitPoint+peakPaddingFrames_post )
                    yThresh_splitRange[z]=yThreshVal*1.2f; //threshold
            }
                plot.setColor(Color.red);
                plot.addPoints(x, yThresh_splitRange, Plot.LINE);
                plot.show();
            IJ.log ("Duplicating stack around peak frames"+Integer.toString(splitPoint-peakPaddingFrames_pre)+"-"+Integer.toString(splitPoint+peakPaddingFrames_post));
            /// 2. perform the split
            ImagePlus img2 = new Duplicator().run(imp,splitPoint-peakPaddingFrames_pre,splitPoint+peakPaddingFrames_post);



            img2.show();
            ImageStack myStack = img2.getStack();

            for (int l=1; l<=peakPaddingFrames_pre+1+peakPaddingFrames_post; l++) {

            myStack.setSliceLabel(Integer.toString(splitPoint-peakPaddingFrames_pre+l), l);
            //IJ.log (Integer.toString(splitPoint-peakPaddingFrames_pre+l));
            }
            img2.setStack(myStack);

            //img2.setTitle(Integer.toString(splitPoint_Counter)+"_"+img2.getTitle()+"-"+Integer.toString(splitPoint-peakPaddingFrames)+"-"+Integer.toString(splitPoint+peakPaddingFrames));
            img2.setTitle(Integer.toString(splitPoint_Counter)+"_splt");
            IJ.log (Integer.toString(splitPoint_Counter)+"_"+img2.getTitle()+"-"+Integer.toString(splitPoint-peakPaddingFrames_pre)+"-"+Integer.toString(splitPoint+peakPaddingFrames_post));
            splitPoint_Counter++;
    }
    IJ.log ("All done. Performed "+Integer.toString(splitPoint_Counter)+" splits.");
    }

    public int findArrayIndexExceedingThreshold ( float [] y, float threshold, int startIdx ) {
        int idx = -1;
        for (int i=startIdx; i< y.length; i++) {
            if ( y[i] > threshold) {
                    idx=i;
                    break;
            }
        }
        return idx;
    }


	public Plot getPlot() {
        myRoiManager= new RoiManager().getInstance();
        plot = new Plot (imp.getTitle()+"_MultiROI_Z-profile", "Time", "FluorNorm", 0);
        // plot.setColor (new Color (rIdx*256/(myRoi_count+1), 255-rIdx*255/(myRoi_count+1),  1+254*(rIdx%2) ) );


	    for (int rIdx=0; rIdx< myRoiManager.getCount(); rIdx++ )
    {
        myRoi_count=myRoiManager.getCount();

		Roi roi = myRoiManager.getRoi(rIdx); //imp.getRoi();
		ImageProcessor ip = imp.getProcessor();
		double minThreshold = ip.getMinThreshold();
		double maxThreshold = ip.getMaxThreshold();
		float[] y;
		boolean hyperstack = imp.isHyperStack();
		if (hyperstack)
			y = getHyperstackProfile(roi, minThreshold, maxThreshold);
		else
			y = getZAxisProfile(roi, minThreshold, maxThreshold);
		if (y==null)
			return null;

		///TODO: Set Start and Stop for X axis: dims_dataset_x_start dims_dataset_x_stop
		//
		dims_dataset_x_start = (int) ((long)y.length*dims_dataset_x_start_pc/100.0f); // offset start is not implemented
		dims_dataset_x_stop = (int) ((long)y.length*params.tF_traceMaxLengthPercent.i/100.0f);

		float[] x = new float[dims_dataset_x_stop];
		float[] y_tr= Arrays.copyOf (y, dims_dataset_x_stop );
        if (printDebugMsg) IJ.log ("Size is " +x.length + " "+y.length+ " "+y_tr.length); ///DEBUG
		y=y_tr;


		String xAxisLabel = showingDialog&&choice.equals(choices[0])?"Frame":"Slice";
		Calibration cal = imp.getCalibration();
		if (cal.scaled()) {
			double c = 1.0f;
			double origin = 0;
			if (timeProfile) {
				c = (float) cal.frameInterval;
				boolean zeroInterval = c==0;
				if (zeroInterval)
					c = 1;
				String timeUnit = zeroInterval?"Frame":"["+cal.getTimeUnit()+"]";
				xAxisLabel = timeUnit;
			} else {
				c = (float) cal.pixelDepth;
				boolean zeroDepth = c==0;
				if (zeroDepth)
					c = 1;
				origin = cal.zOrigin;
				String depthUnit = zeroDepth?"Slice":"["+cal.getZUnit()+"]";
				xAxisLabel = depthUnit;
			}
			for (int i=0; i<x.length; i++)
				x[i] = (float)((i-cal.zOrigin)*c);
		} else {
                    for (int i=0; i<x.length; i++)
				x[i] = (float)i;
		}
		String title;
		if (roi!=null) {
			Rectangle r = roi.getBounds();
			title = imp.getTitle()+"-"+r.x+"-"+r.y;
		} else
			title = imp.getTitle()+"-0-0";

        if (  params.cb_TraceParameters_ClipOutOfRangeValues.b ) { //remove min/max values
            float lowCutoff= params.tF_traceClipBelow.i;
            float highCutoff= params.tF_traceClipAbove.i;
            for (int i=0; i<y.length; i++) {
                if (y[i] < lowCutoff) y[i] = lowCutoff;
                if (y[i] > highCutoff) y[i] = highCutoff;
            }
        }

		//String xAxisLabel = showingDialog && choice.equals(choices[0])?"Frame":"Slice";
		 //plot = new Plot(title, xAxisLabel, "Mean", x, y);
		 //ymin = ProfilePlot.getFixedMin(); //plot Y scale
		 //ymax= ProfilePlot.getFixedMax();
		if (  !params.trace_scaling_options_OriginalScale.b ) { //override Y scale size since we distribute traces

		if (firstTime) {
                ymin=0;
                ymax=1;
            }
		}






		 if ( params.trace_scaling_options_AlignBaselineWithoutNormalization.b ) {

        ymin=findMin (y)-findMean(y);
		ymax=findMax (y)-findMean(y);
		 }

        if (params.cb_TraceDisplay_IncrementalColorCode.b) plot.setColor (new Color (rIdx*256/(myRoi_count+1), 255-rIdx*255/(myRoi_count+1),  1+254*(rIdx%2) ) );

        float yMin, yMax, yMean; //for each trace


        yMin=findMin(y);
        yMean=findMean(y);
        yMax=findMax(y);




/// normalization / distribution of traces




        double traceOffset= 0.0;
           if ( !params.trace_scaling_options_OriginalScale.b ) {
                traceOffset=ymax/(myRoi_count+0)*rIdx;
           }
        double traceDivisor = (yMax-yMin)/(ymax/(myRoi_count+1));
        double traceSubtract = yMin;
            if ( params.trace_scaling_options_NormalizeToSpecificValuesBelow.b ) {
            traceDivisor = params.tF_traceScalingDivide.i;
            traceSubtract = params.tF_traceScalingSubtract.i;
            }
            if ( params.trace_scaling_options_DistributeWithoutNormalization.b || params.trace_scaling_options_OriginalScale.b  ) {
            traceDivisor=1.0;
            traceSubtract=0.0;
            }
             if ( params.trace_scaling_options_AlignBaselineWithoutNormalization.b ) { ///new option
            traceDivisor = 1.0;
            traceSubtract = findMean(y);
            traceOffset= 0.0;
            }


        for (int i=0; i<y.length; i++) { // scale and add an offset to each trace.
                y[i]=(float)( ( (y[i]-traceSubtract)/ traceDivisor ) + traceOffset );
        }

///TODO: disable TXT File as TimeTag
		if ( params.cb_TraceDisplay_UseXColumnOfTextFileAsTimeCode.b )	for (int j=0; j<x.length; j++) x[j] =(x[j]-x[0])/(x[x.length-1]-x[0]) *lastTimeTag ; //rescale the X axis to match the time


          if (  params.cb_TraceDisplay_DrawOriginal.b ) {
                plot.addPoints(x, y, Plot.LINE);
                //plot.setColor(Color.black);
                plot.addPoints(x, y, Plot.DOT) ; // Plot.LINE);
          }

       float [] yFilt;
       if (  params.cb_TraceDisplay_DrawFiltered.b ) {
            yFilt = duplicateFloatArray (y);
            doFilterRunningAverage (yFilt, params.tF_FilteredTrace_running_average_radius.i );
            plot.setColor(Color.black);
            plot.addPoints(x, yFilt, Plot.LINE);
       }

		//IAS mod
		imageTraceLength=x.length;



		float[] y2;
		y2= new float [y.length];
		//for (int i=0; i<y.length; i++) { y2[i]=y[i]+2; }
		//plot.addPoints(x, y2, Plot.LINE); ///ias

		///IAS mod
		if (x.length<=60 && params.cb_TraceDisplay_DrawOriginal.b ) { // draw individual points for short datasets
			plot.setColor(Color.red);
			plot.addPoints(x, y, Plot.CIRCLE);
			plot.setColor(Color.black);
		}

		        ///override min and max to global
        if (ymin > findMin (y)) ymin = findMin(y);
        if (ymax < findMax (y)) ymax = findMax(y);

        IJ.log ("Min/max are "+ymin+ " "+ymax);
       // double[] a = Tools.getMinMax(x);
        //double xmin=a[0]; double xmax=a[1];
       // plot.setLimits(xmin, xmax, ymin, ymax);


		if (!(ymin==0.0 && ymax==0.0)) { //set scale for the display
			double[] a = Tools.getMinMax(x);
			double xmin=a[0]; double xmax=a[1];
			plot.setLimits(xmin, xmax, ymin, ymax);
		}


		if (!firstTime) { //draws the "data position" vertical bar
			int pos = imp.getCurrentSlice();
			int size = imp.getStackSize();
			if (hyperstack) {
				if (timeProfile) {
					pos = imp.getT();
					size = imp.getNFrames();
				} else {
					pos = imp.getZ();
					size = imp.getNSlices();
				}
			}
			double xx = (pos-1.0)/(size-1.0)/(params.tF_traceMaxLengthPercent.i/100.0f); //normalize also by the displayed trace length
			if (xx==0.0)
				plot.setLineWidth(2);
			plot.setColor(Color.blue);
			plot.drawNormalizedLine(xx, 0, xx, 1.0);
			//plot.setColor(Color.black);
			plot.setLineWidth(1);
		}
		firstTime = false;
	    }
        ///electrical trace add
        ///TODO: Set Start and Stop for X axis: dims_dataset_x_start dims_dataset_x_stop

        int dims_dataset_EL_start = (int) ((long)i*dims_dataset_x_start_pc/100.0f); // offset start is not implemented
        int dims_dataset_EL_stop  = (int) ((long)i*params.tF_traceMaxLengthPercent.i/100.0f); ///TODO: for very long traces, we might have overrun
        if (printDebugMsg) IJ.log ("EL dataset new dimension is "+dims_dataset_EL_stop );
            ptX= new float [dims_dataset_EL_stop];
            ptY= new float [dims_dataset_EL_stop];
        //i=dims_dataset_EL_stop;


        for (int c=0; c<dims_dataset_EL_stop; c++) {
                ptX[c]=(float)c/dims_dataset_EL_stop *imageTraceLength;
                ptY[c]=  (float)( (pt3d[c]))   ;
         }
        float elMean=findMean(ptY);
        for (int c=0; c<dims_dataset_EL_stop; c++) {
                ptX[c]=(float)c/dims_dataset_EL_stop *imageTraceLength;
                ptY[c]=  (float)( (pt3d[c]-elMean)*ymax)   ; ///TODO renormalization MAX
         }
         plot.setColor(Color.red);
         		if ( params.cb_TraceDisplay_UseXColumnOfTextFileAsTimeCode.b && useTXTFileFirstColumnAsTimetag)
                    for (int j=0; j<ptX.length; j++) ptX[j] =(ptX[j]-ptX[0])/(ptX[ptX.length-1]-ptX[0]) *lastTimeTag ; //rescale the X axis to match the time

		if (  params.cb_TraceDisplay_DrawElectricalTraceOverlay.b ) {// disable EL trace display
                plot.addPoints(ptX, ptY, Plot.LINE);
		}
		return plot;
	}

	public ImagePlus getSourceImage() {
		return imp;
	}

	private float[] getHyperstackProfile(Roi roi, double minThreshold, double maxThreshold) {
		int slices = imp.getNSlices();
		int frames = imp.getNFrames();
		int c = imp.getC();
		int z = imp.getZ();
		int t = imp.getT();
		int size = slices;
		if (firstTime)
			timeProfile = slices==1 && frames>1;
		if (slices>1 && frames>1 && (!isPlotMaker ||firstTime)) {
			showingDialog = true;
			GenericDialog gd = new GenericDialog("Profiler");
			gd.addChoice("Profile", choices, choice);
			gd.showDialog();
			if (gd.wasCanceled())
				return null;
			choice = gd.getNextChoice();
			timeProfile = choice.equals(choices[0]);
		}
		if (timeProfile)
			size = frames;
		else
			size = slices;
		float[] values = new float[size];
		Calibration cal = imp.getCalibration();
		Analyzer analyzer = new Analyzer(imp);
		int measurements = Analyzer.getMeasurements();
		boolean showResults = !isPlotMaker && measurements!=0 && measurements!=LIMIT;
		measurements |= MEAN;
		if (showResults) {
			if (!Analyzer.resetCounter())
				return null;
		}
		ImageStack stack = imp.getStack();
		for (int i=1; i<=size; i++) {
			int index = 1;
			if (timeProfile)
				index = imp.getStackIndex(c, z, i);
			else
				index = imp.getStackIndex(c, i, t);
			ImageProcessor ip = stack.getProcessor(index);
			if (minThreshold!=ImageProcessor.NO_THRESHOLD)
				ip.setThreshold(minThreshold,maxThreshold,ImageProcessor.NO_LUT_UPDATE);
			ip.setRoi(roi);
			ImageStatistics stats = ImageStatistics.getStatistics(ip, measurements, cal);
			analyzer.saveResults(stats, roi);
			values[i-1] = (float)stats.mean;
		}
		if (showResults) {
			ResultsTable rt = Analyzer.getResultsTable();
			rt.show("Results");
		}
		return values;
	}

	private float[] getZAxisProfile(Roi roi, double minThreshold, double maxThreshold) {
		ImageStack stack = imp.getStack();
		if (firstTime) {
			int slices = imp.getNSlices();
			int frames = imp.getNFrames();
			timeProfile = slices==1 && frames>1;
		}
		int size = stack.getSize();
		float[] values = new float[size];
		Calibration cal = imp.getCalibration();
		Analyzer analyzer = new Analyzer(imp);
		int measurements = Analyzer.getMeasurements();
		boolean showResults = !isPlotMaker && measurements!=0 && measurements!=LIMIT;
		boolean showingLabels = firstTime && showResults && ((measurements&LABELS)!=0 || (measurements&SLICE)!=0);
		measurements |= MEAN;
		if (showResults) {
			if (!Analyzer.resetCounter())
				return null;
		}
		boolean isLine = roi!=null && roi.isLine();
		int current = imp.getCurrentSlice();
		for (int i=1; i<=size; i++) {
			if (showingLabels)
				imp.setSlice(i);
			ImageProcessor ip = stack.getProcessor(i);
			if (minThreshold!=ImageProcessor.NO_THRESHOLD)
				ip.setThreshold(minThreshold,maxThreshold,ImageProcessor.NO_LUT_UPDATE);
			ip.setRoi(roi);
			ImageStatistics stats = null;
			if (isLine)
				stats = getLineStatistics(roi, ip, measurements, cal);
			else
				stats = ImageStatistics.getStatistics(ip, measurements, cal);
			analyzer.saveResults(stats, roi);
			values[i-1] = (float)stats.mean;
		}
		if (showResults) {
			ResultsTable rt = Analyzer.getResultsTable();
			rt.show("Results");
		}
		if (showingLabels)
			imp.setSlice(current);
		return values;
	}

	private ImageStatistics getLineStatistics(Roi roi, ImageProcessor ip, int measurements, Calibration cal) {
		ImagePlus imp = new ImagePlus("", ip);
		imp.setRoi(roi);
		ProfilePlot profile = new ProfilePlot(imp);
		double[] values = profile.getProfile();
		ImageProcessor ip2 = new FloatProcessor(values.length, 1, values);
		return ImageStatistics.getStatistics(ip2, measurements, cal);
	}


/// user functions


public float findMax (float [] sourceC) { // find the mean of subregion of a data array
    if (sourceC.length==0) {
              // cout << "mean: length is ZERO, returning 0" << endl;
            return 0;
    }
    float cMax=(float)-1e22;
    for (int r= 0; r<sourceC.length; r++ ) { if (sourceC[r] > cMax ) {cMax=sourceC[r];} }
    return cMax;
}
public float findMin (float [] sourceC) { // find the mean of subregion of a data array
    if (sourceC.length==0) {
              // cout << "mean: length is ZERO, returning 0" << endl;
            return 0;
    }
    float cMax=(float)1e22;
    for (int r= 0; r<sourceC.length; r++ ) { if (sourceC[r] < cMax ) {cMax=sourceC[r];} }
    return cMax;
}
public float findMean (float [] sourceC) { // find the mean of subregion of a data array
    if (sourceC.length==0) {
              // cout << "mean: length is ZERO, returning 0" << endl;
            return 0;
    }
    double cMax=0;
    //int cnt=0;
    for (int r= 0; r<sourceC.length; r++ ) { cMax+=sourceC[r]; }
    return (float) (cMax/sourceC.length);
}

public int doFilterRunningAverage (float [] sourceC, int filtRad) { // perform the running average filter on the array
    if (sourceC.length==0) {
        IJ.log ("ERROR: source buffer too short in function doFilterRunningAverage");
        return 0;
    }//
    float [] fTmp=new float [sourceC.length];
    double sum=0;
    double count =0;
    for (int r= 0; r<sourceC.length; r++ ) { fTmp[r]=sourceC[r]; }
    for (int r= filtRad; r<sourceC.length-filtRad; r++ ) {
        sum=0;
        count=0;
        for (int off=-filtRad; off<=filtRad; off++) {
                if (r+off>=sourceC.length || r+off <0 ) {
                    IJ.log ("ERROR: source buffer overrun in function doFilterRunningAverage");
                    return -1;
                }
            sum+=fTmp[r+off];
            count++;
        }
        sourceC[r]=(float) (sum/count);
    }
    return (int) count; //return filter diameter
}

public float [] duplicateFloatArray (float [] sourceC) {
    if (sourceC.length==0) {
        IJ.log ("ERROR: source buffer too short in function doFilterRunningAverage");
        return (float []) null;
    }//
    float [] fTmp=new float [sourceC.length];
    for (int r= 0; r<sourceC.length; r++ ) { fTmp[r]=sourceC[r]; }
    return fTmp;
}

///user classes for UI input handling

public void displayControlDialog (boolean doDisp) {
    if (myPlotControlDialog== null)   myPlotControlDialog = new PlotControlDialog();
    myPlotControlDialog.setVisible (doDisp) ; // WindowManager.addWindow (myPlotControlDialog);
}

class PlotControlDialog extends JFrame implements ActionListener, DocumentListener
{  public PlotControlDialog () //(ImageCanvas sc)
   {  //this.spIc = sc;
    setTitle("MultiROI analysis options");
    setSize(400,500);

    Container contentPane = getContentPane();

    JPanel controlWindowMainPanel = new JPanel();
    controlWindowMainPanel.setLayout(new GridLayout(4,1, 0, 0));

    JPanel a_subPanel = new JPanel(); //Trace scaling options
    a_subPanel.setLayout(new GridLayout(0,1));

    JPanel b_subPanel = new JPanel(); //Trace display options
    b_subPanel.setLayout(new GridLayout(0, 1, 20, 0));

    JPanel c_subPanel = new JPanel(); //Trace display options
    c_subPanel.setLayout(new GridLayout(0, 1, 0, 0));

    JPanel d_subPanel = new JPanel(); // Trace parameters
    d_subPanel.setLayout(new GridLayout(0, 2, 20, 0));


    JLabel tL_a00 = new JLabel("Trace scaling options:");
    a_subPanel.add(tL_a00);

    trace_scaling_options_OriginalScale = new JRadioButton("Original scale for each trace");
    trace_scaling_options_NormalizeAndDistribute = new JRadioButton("Normalize and distribute each trace");
    trace_scaling_options_NormalizeAndDistribute.setSelected(true);
    trace_scaling_options_DistributeWithoutNormalization = new JRadioButton("Distribute without normalization");
    trace_scaling_options_AlignBaselineWithoutNormalization = new JRadioButton("Align baseline without normalization");
    trace_scaling_options_NormalizeToSpecificValuesBelow = new JRadioButton("Normalize all to specific Values Below");

    addRadioButtonToPanel (trace_scaling_options_OriginalScale, a_subPanel, group, "trace_scaling_options_OriginalScale");
    addRadioButtonToPanel (trace_scaling_options_NormalizeAndDistribute, a_subPanel, group, "trace_scaling_options_NormalizeAndDistribute");
    addRadioButtonToPanel (trace_scaling_options_DistributeWithoutNormalization, a_subPanel, group, "trace_scaling_options_DistributeWithoutNormalization");
    addRadioButtonToPanel (trace_scaling_options_AlignBaselineWithoutNormalization, a_subPanel, group, "trace_scaling_options_AlignBaselineWithoutNormalization");
    addRadioButtonToPanel (trace_scaling_options_NormalizeToSpecificValuesBelow, a_subPanel, group, "trace_scaling_options_NormalizeToSpecificValuesBelow");
    //---------------------------------
    JLabel tL_a01 = new JLabel("Subtract:");
    b_subPanel.add(tL_a01);
    tF_traceScalingSubtract = new JTextField(Integer.toString(params.getInteger ("tF_traceScalingSubtract")) , 0);
    tF_traceScalingSubtract.getDocument().addDocumentListener(this);
    tF_traceScalingSubtract.getDocument().putProperty("TitleProperty", "tF_traceScalingSubtract" );
    /// .setEditable(false);
    b_subPanel.add(tF_traceScalingSubtract);


    JLabel tL_a02 = new JLabel("Divide:");
    b_subPanel.add(tL_a02);
    tF_traceScalingDivide = new JTextField(Integer.toString(params.getInteger ("tF_traceScalingDivide")) , 0);
    tF_traceScalingDivide.getDocument().addDocumentListener(this);
    tF_traceScalingDivide.getDocument().putProperty("TitleProperty", "tF_traceScalingDivide" );
    b_subPanel.add(tF_traceScalingDivide);





//---------------------------------

///TODO: Add backward variable/UI sync: currently, there is no active feedback from the variables (e.g. display parameters) back to the UI

 cb_TraceDisplay_DrawOriginal = new JCheckBox ("Display Original trace"); ///TODO display options
    cb_TraceDisplay_DrawOriginal.setSelected(true);
 cb_TraceDisplay_DrawFiltered = new JCheckBox ("Display Filtered trace");
    cb_TraceDisplay_DrawFiltered.setSelected(true);
 cb_TraceDisplay_IncrementalColorCode = new JCheckBox ("Incremental color code");
    cb_TraceDisplay_IncrementalColorCode.setSelected(true);
 cb_TraceDisplay_DrawStatsForEachTrace = new JCheckBox ("Draw Stats for each trace");
 cb_TraceDisplay_DrawStatsForEachTrace.setEnabled(false); ///TODO statistics display -- re-use the old demo code
 cb_TraceDisplay_DrawElectricalTraceOverlay = new JCheckBox ("Draw Electrical Trace overlay");
    cb_TraceDisplay_DrawElectricalTraceOverlay.setSelected(true);

 cb_TraceDisplay_UseXColumnOfTextFileAsTimeCode = new JCheckBox ("Use X column of text file as time code");


addCheckboxToPanel (c_subPanel, cb_TraceDisplay_DrawOriginal , "cb_TraceDisplay_DrawOriginal" );
addCheckboxToPanel (c_subPanel, cb_TraceDisplay_DrawFiltered , "cb_TraceDisplay_DrawFiltered" );
addCheckboxToPanel (c_subPanel, cb_TraceDisplay_IncrementalColorCode , "cb_TraceDisplay_IncrementalColorCode" );
addCheckboxToPanel (c_subPanel, cb_TraceDisplay_DrawStatsForEachTrace , "cb_TraceDisplay_DrawStatsForEachTrace" );
addCheckboxToPanel (c_subPanel, cb_TraceDisplay_DrawElectricalTraceOverlay , "cb_TraceDisplay_DrawElectricalTraceOverlay" );
addCheckboxToPanel (c_subPanel, cb_TraceDisplay_UseXColumnOfTextFileAsTimeCode , "cb_TraceDisplay_UseXColumnOfTextFileAsTimeCode" );


    JLabel tL_a03 = new JLabel("Trace parameters:");
    d_subPanel.add(tL_a03);
JLabel tL_a03_b = new JLabel(" ");
    d_subPanel.add(tL_a03_b);

    JLabel tL_a04 = new JLabel("Max length (%):");
    d_subPanel.add(tL_a04);
    tF_traceMaxLengthPercent = new JTextField(Integer.toString(params.getInteger ("tF_traceMaxLengthPercent")) , 0);
    tF_traceMaxLengthPercent.getDocument().addDocumentListener(this);
        tF_traceMaxLengthPercent.getDocument().putProperty("TitleProperty", "tF_traceMaxLengthPercent" );
    d_subPanel.add(tF_traceMaxLengthPercent);

 cb_TraceParameters_ClipOutOfRangeValues = new JCheckBox ("Clip out-of-range values:");
addCheckboxToPanel (d_subPanel, cb_TraceParameters_ClipOutOfRangeValues, "cb_TraceParameters_ClipOutOfRangeValues" );


    JLabel tL_a05 = new JLabel(" ");
    d_subPanel.add(tL_a05);

    JLabel tL_a06 = new JLabel("Clip below:");
    d_subPanel.add(tL_a06);
        tF_traceClipBelow = new JTextField(Integer.toString(params.getInteger ("tF_traceClipBelow")) , 0);
        tF_traceClipBelow.getDocument().addDocumentListener(this);
            tF_traceClipBelow.getDocument().putProperty("TitleProperty", "tF_traceClipBelow" );
        d_subPanel.add(tF_traceClipBelow);
    JLabel tL_a07 = new JLabel("Clip above:");
    d_subPanel.add(tL_a07);
        tF_traceClipAbove = new JTextField(Integer.toString(params.getInteger ("tF_traceClipAbove")) , 0);
        tF_traceClipAbove.getDocument().addDocumentListener(this);
            tF_traceClipAbove.getDocument().putProperty("TitleProperty", "tF_traceClipAbove" );
        d_subPanel.add(tF_traceClipAbove);


      JLabel tL_a08 = new JLabel("Copy samples before peak:");
    d_subPanel.add(tL_a08);
        tF_Split_shoulder_Pre = new JTextField(Integer.toString(params.getInteger ("tF_Split_shoulder_Pre")) , 0);
        tF_Split_shoulder_Pre.getDocument().addDocumentListener(this);
            tF_Split_shoulder_Pre.getDocument().putProperty("TitleProperty", "tF_Split_shoulder_Pre" );
        d_subPanel.add(tF_Split_shoulder_Pre);

      JLabel tL_a09 = new JLabel("Samples after peak:");
     d_subPanel.add(tL_a09);
        tF_Split_shoulder_Post = new JTextField(Integer.toString(params.getInteger ("tF_Split_shoulder_Post")) , 0);
        tF_Split_shoulder_Post.getDocument().addDocumentListener(this);
            tF_Split_shoulder_Post.getDocument().putProperty("TitleProperty", "tF_Split_shoulder_Post" );
        d_subPanel.add(tF_Split_shoulder_Post);


      JLabel tL_a10 = new JLabel("Filtered trace (running ave rad):");
     d_subPanel.add(tL_a09);
        tF_FilteredTrace_running_average_radius = new JTextField(Integer.toString(params.getInteger ("tF_FilteredTrace_running_average_radius")) , 0);
        tF_FilteredTrace_running_average_radius.getDocument().addDocumentListener(this);
            tF_FilteredTrace_running_average_radius.getDocument().putProperty("TitleProperty", "tF_FilteredTrace_running_average_radius" );
        d_subPanel.add(tF_FilteredTrace_running_average_radius);


    btnRefreshPlot= new Button("Make a New Plot Window!");
    btnRefreshPlot.setBackground(Color.cyan);
    btnRefreshPlot.addActionListener(this);
    btnRefreshPlot.setEnabled(true);

    JLabel tL_a100 = new JLabel("NB: Use can also use Live button to refresh existing plot windows!");
        b_subPanel.add(tL_a100);
        b_subPanel.add(btnRefreshPlot);


    btnSplitUp= new Button("Split up intervals over threshold!");
    btnSplitUp.setBackground(Color.red);
    btnSplitUp.addActionListener(this);
    btnSplitUp.setEnabled(true);

        b_subPanel.add(btnSplitUp);

/*
 btnCheckForUpdatesOnline= new Button("Online update");
    btnCheckForUpdatesOnline.setBackground(Color.green);
    btnCheckForUpdatesOnline.addActionListener(this);
    btnCheckForUpdatesOnline.setEnabled(true);
d_subPanel.add(btnCheckForUpdatesOnline);
*/


      controlWindowMainPanel.add(a_subPanel);
      controlWindowMainPanel.add(b_subPanel);
      controlWindowMainPanel.add(c_subPanel);
      controlWindowMainPanel.add(d_subPanel);
      contentPane.add(controlWindowMainPanel);
   }
   public void actionPerformed(ActionEvent evt)
   {  Object source = evt.getSource();

      if (source == btnRefreshPlot) {

        if (printDebugMsg) IJ.log ("Refreshing the plot...");
        		Plot plot = getPlot();
		if (plot!=null) {
			if (isPlotMaker)
				plot.setPlotMaker(myPlotMakerRef);
			plot.show();

		}
        //plot.setFrozen (false);
      }
      if (source == btnSplitUp) {

        if (printDebugMsg) IJ.log ("Splitting up the dataset...");
doSplitUpDataSetOverThreshold ();
        //plot.setFrozen (false);
      }


       if (printDebugMsg)  IJ.log ("Event source "+ source.toString());
       if (printDebugMsg)  IJ.log ("class "+ source.getClass().toString());
       if (printDebugMsg)  IJ.log (mapControls_Names.get(source));

        // Strategy to assign the event from the control to the right constant:
        // look up the string name of the control from mapControls_Names.get(source)
        // look up the parameter variable from the params.get(string))
        // assign
        // handle the text fields through the Document updates
        //params.chooseRadio ( mapControls_Names.get(source) );
        params.setCheckboxValue ( mapControls_Names.get(source), source.getClass().toString() );





   }

    public void insertUpdate(DocumentEvent e) {
        if (printDebugMsg) IJ.log ("insertUpdate: property "+  e.getDocument().getProperty("TitleProperty")); //+e.getDocument().TitleProperty
        updateValuesAfterTextFieldChange (e);
   }

   public void removeUpdate(DocumentEvent e) {
       if (printDebugMsg) IJ.log ("removeUpdate:");
   }

   public void changedUpdate(DocumentEvent e) {
       if (printDebugMsg) IJ.log ("Changed Update.");
       updateValuesAfterTextFieldChange (e);
   }

   public void updateValuesAfterTextFieldChange (DocumentEvent e) {

        ///DEBUG start
        if (printDebugMsg) IJ.log ("Text Field: "+  e.getDocument().getProperty("TitleProperty")); //+e.getDocument().TitleProperty

        try {
            params.setInteger ( (String)e.getDocument().getProperty("TitleProperty"), e.getDocument().getText(0, e.getDocument().getLength()).trim()  );
        }
        catch(BadLocationException ex){
            IJ.log("Failed to read the text box.");
        }
        // dims_dataset_x_stop_pc=tF_val; ///TODO
   }



    void addRadioButtonToPanel(JRadioButton b, JPanel p, ButtonGroup g){
        b.addActionListener(this);
        p.add(b);
        g.add(b);
    }
    void addRadioButtonToPanel(JRadioButton b, JPanel p, ButtonGroup g, String controlName){
        b.addActionListener(this);
        p.add(b);
        g.add(b);
        mapControls_Names.put (b, controlName );
    }

    void addButtonToPanel(Button b, JPanel p) {
        b.addActionListener(this);
        p.add(b);
    }
    void addCheckboxToPanel( JPanel p, JCheckBox b) {
        b.addActionListener(this);
        p.add(b);
    }
    void addCheckboxToPanel( JPanel p, JCheckBox b, String controlName) {
        b.addActionListener(this);
        p.add(b);
        mapControls_Names.put (b, controlName );
    }
    // CONTROL HANDLES
    private JRadioButton trace_scaling_options_OriginalScale, trace_scaling_options_NormalizeAndDistribute, trace_scaling_options_DistributeWithoutNormalization,trace_scaling_options_AlignBaselineWithoutNormalization, trace_scaling_options_NormalizeToSpecificValuesBelow;
    private JTextField tF_traceScalingSubtract, tF_traceScalingDivide, tF_traceMaxLengthPercent, tF_traceClipBelow, tF_traceClipAbove, tF_Split_shoulder_Pre, tF_Split_shoulder_Post, tF_FilteredTrace_running_average_radius  ;
    private JCheckBox cb_TraceDisplay_DrawOriginal, cb_TraceDisplay_DrawFiltered, cb_TraceDisplay_IncrementalColorCode,
    cb_TraceDisplay_DrawStatsForEachTrace, cb_TraceDisplay_DrawElectricalTraceOverlay, cb_TraceDisplay_UseXColumnOfTextFileAsTimeCode, cb_TraceParameters_ClipOutOfRangeValues;

    private ButtonGroup group = new ButtonGroup();

    Map <Object, String> mapControls_Names = new HashMap <Object, String>();    // container variable declarations and preferences table updates

    private Button btnRefreshPlot;
    private Button btnSplitUp;
    private Button btnCheckForUpdatesOnline;


}


public class traceDisplayParametersContainer { // class to maintain and read-in preferences from the text boxes
    // preferences are kept as Name(string)-Name-variable MAPS
    // since it is impossible to store references/pointers to primitive data types in an easy way, a conversion step will be required
    public traceDisplayParametersContainer() {

        //radio buttons only: these will be checked, and made mutually exclusive
        variableNames_CodeRefs_radio.put ( "trace_scaling_options_OriginalScale"  , trace_scaling_options_OriginalScale  );
        variableNames_CodeRefs_radio.put ( "trace_scaling_options_NormalizeAndDistribute"  , trace_scaling_options_NormalizeAndDistribute  );
        variableNames_CodeRefs_radio.put ( "trace_scaling_options_DistributeWithoutNormalization"  , trace_scaling_options_DistributeWithoutNormalization  );
        variableNames_CodeRefs_radio.put ( "trace_scaling_options_AlignBaselineWithoutNormalization"  , trace_scaling_options_AlignBaselineWithoutNormalization  );

        variableNames_CodeRefs_radio.put ( "trace_scaling_options_NormalizeToSpecificValuesBelow"  , trace_scaling_options_NormalizeToSpecificValuesBelow  );
        //text boxes with integer values
        variableNames_CodeRefs_int.put ( "tF_traceScalingSubtract"  , tF_traceScalingSubtract  );
        variableNames_CodeRefs_int.put ( "tF_traceScalingDivide"  , tF_traceScalingDivide  );
        variableNames_CodeRefs_int.put ( "tF_traceMaxLengthPercent"  , tF_traceMaxLengthPercent  );
        variableNames_CodeRefs_int.put ( "tF_traceClipBelow"  , tF_traceClipBelow  );
        variableNames_CodeRefs_int.put ( "tF_traceClipAbove"  , tF_traceClipAbove  );
        variableNames_CodeRefs_int.put ( "tF_Split_shoulder_Pre"  , tF_Split_shoulder_Pre  );
        variableNames_CodeRefs_int.put ( "tF_Split_shoulder_Post"  , tF_Split_shoulder_Post  );
        variableNames_CodeRefs_int.put ( "tF_FilteredTrace_running_average_radius"  , tF_FilteredTrace_running_average_radius  );


        //all boolean buttons
        variableNames_CodeRefs_bool.put ( "cb_TraceDisplay_DrawOriginal"  , cb_TraceDisplay_DrawOriginal  );
        variableNames_CodeRefs_bool.put ( "cb_TraceDisplay_DrawFiltered"  , cb_TraceDisplay_DrawFiltered  );
        variableNames_CodeRefs_bool.put ( "cb_TraceDisplay_IncrementalColorCode"  , cb_TraceDisplay_IncrementalColorCode  );
        variableNames_CodeRefs_bool.put ( "cb_TraceDisplay_DrawStatsForEachTrace"  , cb_TraceDisplay_DrawStatsForEachTrace  );
        variableNames_CodeRefs_bool.put ( "cb_TraceDisplay_DrawElectricalTraceOverlay"  , cb_TraceDisplay_DrawElectricalTraceOverlay  );
        variableNames_CodeRefs_bool.put ( "cb_TraceDisplay_UseXColumnOfTextFileAsTimeCode"  , cb_TraceDisplay_UseXColumnOfTextFileAsTimeCode  ); //*/
        variableNames_CodeRefs_bool.put ( "cb_TraceParameters_ClipOutOfRangeValues"  , cb_TraceParameters_ClipOutOfRangeValues  ); //*/
        //duplicate radio buttons here
        variableNames_CodeRefs_bool.put ( "trace_scaling_options_OriginalScale"  , trace_scaling_options_OriginalScale  );
        variableNames_CodeRefs_bool.put ( "trace_scaling_options_NormalizeAndDistribute"  , trace_scaling_options_NormalizeAndDistribute  );
        variableNames_CodeRefs_bool.put ( "trace_scaling_options_DistributeWithoutNormalization"  , trace_scaling_options_DistributeWithoutNormalization  );
        variableNames_CodeRefs_bool.put ( "trace_scaling_options_AlignBaselineWithoutNormalization"  , trace_scaling_options_AlignBaselineWithoutNormalization  );
        variableNames_CodeRefs_bool.put ( "trace_scaling_options_NormalizeToSpecificValuesBelow"  , trace_scaling_options_NormalizeToSpecificValuesBelow  );


        //resetParentVariables ();
    }

    public void chooseRadio ( String key ) {
        //set all the radio button variables to false, then the one in Key to true
        if (printDebugMsg) IJ.log (key +" is looked up inside RADIO buttons "); ///DEBUG

        if (variableNames_CodeRefs_radio.get (key).b) {
            for (String k: variableNames_CodeRefs_radio.keySet()) {
                variableNames_CodeRefs_radio.get(k).parseBoolean ("false");
            }
        }
        variableNames_CodeRefs_radio.get (key).parseBoolean ("true");
        if (printDebugMsg) IJ.log (key +" is now "+variableNames_CodeRefs_radio.get (key).b); ///DEBUG
    }

    public void setCheckboxValue ( String key, String sourceClassString ) {

        variableNames_CodeRefs_bool.get (key).b=!variableNames_CodeRefs_bool.get (key).b;

        if (printDebugMsg) IJ.log (key +" is now "+variableNames_CodeRefs_bool.get (key).b); ///DEBUG
        chooseRadio (key);
    }

    public void setInteger ( String key, String val ) {
        variableNames_CodeRefs_int.get (key).parseInt (val);
    }

        public int getInteger ( String key ) {
        return variableNames_CodeRefs_int.get (key).i;
    }

    public void updateParentVariables () {

    }
    public void resetParentVariables () {
        for (String k: variableNames_CodeRefs_radio.keySet()) {
            variableNames_CodeRefs_radio.get(k).parseBoolean ("false");
        }
        for (String k: variableNames_CodeRefs_int.keySet()) {
            variableNames_CodeRefs_int.get(k).parseInt ("100");
        }
        for (String k: variableNames_CodeRefs_bool.keySet()) {
            variableNames_CodeRefs_bool.get(k).parseBoolean ("false");
        }
    }
    public void printAllVariables () {
        for (String k: variableNames_CodeRefs_int.keySet()) {
            IJ.log ("key: " + k + " \tvalue: " + variableNames_CodeRefs_int.get(k).i);
        }
    }


    public cBoolean  trace_scaling_options_OriginalScale = new cBoolean (false), trace_scaling_options_NormalizeAndDistribute = new cBoolean (false),
                    trace_scaling_options_DistributeWithoutNormalization  = new cBoolean (false), trace_scaling_options_AlignBaselineWithoutNormalization  = new cBoolean (false), trace_scaling_options_NormalizeToSpecificValuesBelow  = new cBoolean (false);
    public cInteger  tF_traceScalingSubtract= new cInteger (20), tF_traceScalingDivide= new cInteger (10), tF_traceMaxLengthPercent= new cInteger (100),
                    tF_traceClipBelow= new cInteger (-10), tF_traceClipAbove = new cInteger (4095) , tF_Split_shoulder_Pre = new cInteger (20), tF_Split_shoulder_Post = new cInteger (100), tF_FilteredTrace_running_average_radius = new cInteger (1);
    public cBoolean  cb_TraceDisplay_DrawOriginal = new cBoolean (true), cb_TraceDisplay_DrawFiltered = new cBoolean (true), cb_TraceDisplay_IncrementalColorCode = new cBoolean (true), cb_TraceDisplay_DrawStatsForEachTrace = new cBoolean (false),
                    cb_TraceDisplay_DrawElectricalTraceOverlay = new cBoolean (true), cb_TraceDisplay_UseXColumnOfTextFileAsTimeCode = new cBoolean (false), cb_TraceParameters_ClipOutOfRangeValues= new cBoolean (false) ;

    public Map <String, cBoolean> variableNames_CodeRefs_radio = new HashMap <String, cBoolean>();
    public Map <String, cInteger> variableNames_CodeRefs_int = new HashMap <String, cInteger>();
    public Map <String, cBoolean> variableNames_CodeRefs_bool = new HashMap <String, cBoolean>();
}

public class cBoolean  {
    public cBoolean (boolean c) {
        b=c;
    }
    public void parseBoolean (String s) {
        b= Boolean.parseBoolean (s);
    }
    public boolean b;
}

public class cInteger  {
    public cInteger (int c) {
        i=c;
    }
    public void parseInt (String s) {
        i=Integer.parseInt(s);
    }
    public int i;
}

public class ContainerVariable  {
public ContainerVariable () {

i=0;
s=new String();
b=false;

}
public int getI () {
return i;
}
public void setI (int j) {
i=j;
}
public int i;
public String s;
public boolean b;

}
public float findStDev (float [] sourceC ) { // cleaned up for java
      //  cout << "stdev" << endl;
    int len2 = sourceC.length;
    if (len2==0) {
            //cout << "stdev: length is ZERO, returning 0" << endl;
            return (float) 0.0;
     }
    double sum2=0;
    double cnt2=0;
    float mean2= findMean (sourceC);

    for (int r= 0; r<len2; r++ ) {
        sum2+=(sourceC[r]-mean2)*(sourceC[r]-mean2);
        cnt2++;
    }
   // cout << "stdev" << sqrt(sum/cnt) << endl;
    if (cnt2==0) return (float) 0.0 ;
     else {
            //   cout << "mean" << sum/cnt << endl;
        return (float) Math.sqrt( sum2/cnt2);
     }
}


}

