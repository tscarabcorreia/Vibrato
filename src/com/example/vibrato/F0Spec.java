package com.example.vibrato;

import java.util.ArrayList;
import java.util.Arrays;
import com.github.mikephil.charting.data.Entry;

public class F0Spec {

	public static ArrayList<Entry> PostProcessing(ArrayList<Entry> entries)
	{
		return TEF(TEI(TEG(entries)));
	}
	
	private static ArrayList<Entry> TEG(ArrayList<Entry> entries)
	{
		int consecutivos = 0;
		for (int i = 1; i < entries.size(); i++)
		{
			if (entries.get(i).getVal() > 2*entries.get(i-1).getVal() || entries.get(i).getVal() < 0.5*entries.get(i-1).getVal())
			{
				if (consecutivos < 5)
				{
					float[] array = {entries.get(i).getVal(), entries.get(i-1).getVal(), entries.get(i-1).getVal()};
					Arrays.sort(array);
					entries.get(i).setVal(array[1]);
					consecutivos++;
				}
			}
			else
			{
				consecutivos = 0;
			}
		}
		return entries;
	}
	
	private static ArrayList<Entry> TEI(ArrayList<Entry> entries)
	{
		int consecutivos = 0;
		float[] array = getArrayFromEntries(entries);
		float mediana = mediana(array);
		double desvioPadrao = desvioPadrao(array);
		for (int i = 1; i < entries.size()-1; i++)
		{
			if (Math.abs(entries.get(i+1).getVal()-entries.get(i).getVal()) > mediana + 2*desvioPadrao)
			{
				if (consecutivos < 5)
				{
					float[] array2 = {entries.get(i).getVal(), entries.get(i-1).getVal(), entries.get(i-1).getVal()};
					entries.get(i).setVal(mediana(array2));
					consecutivos++;
				}
			}
			else
			{
				consecutivos = 0;
			}
		}
		return entries;
	}

	private static float[] getArrayFromEntries(ArrayList<Entry> entries) {
		float[] array = new float[entries.size()];
		for (int i = 0; i < entries.size(); i++){
			array[i] = entries.get(i).getVal();
		}
		return array;
	}
	
	private static ArrayList<Entry> TEF(ArrayList<Entry> entries)
	{
		int consecutivos = 0;
		for (int i = 1; i < entries.size()-1; i++)
		{
			if (Math.signum(entries.get(i-1).getVal()) != Math.signum(entries.get(i).getVal()) 
					&& Math.signum(entries.get(i).getVal()) != Math.signum(entries.get(i+1).getVal()))
			{
				if (consecutivos < 5)
				{
					float[] array2 = {entries.get(i).getVal(), entries.get(i-1).getVal(), entries.get(i-1).getVal()};
					entries.get(i).setVal(mediana(array2));
					consecutivos++;
				}
			}
			else
			{
				consecutivos = 0;
			}
		}
		return entries;
	}
	
	private static float mediana(float[] array)
	{
		Arrays.sort(array);
        if (array.length % 2 == 1) {
              return array[((array.length + 1) / 2) - 1];
        } else {
              int m = array.length / 2;
              return (array[m - 1] + array[m]) / 2;
        }
	}
	
	private static float medianaNaoNula(float[] array)
	{
		Arrays.sort(array);
		int posNaoNula = 0;
		for (int i = 0; i < array.length; i++)
		{
			if (array[i] > 0)
			{
				posNaoNula = i;
				break;
			}
		}
		return mediana(Arrays.copyOfRange(array, posNaoNula, array.length));
	}
	private static double desvioPadrao(float[] array)
	{
		return Math.sqrt(variancia(array));
	}
	
	private static double variancia(float[] array)
	{
		double p1 = 1 / Double.valueOf(array.length - 1);
        double p2 = somaDoQuadradoDosElementos(array) - (Math.pow(somaDosElementos(array), 2) / Double.valueOf(array.length));
        return p1 * p2;
	}

	private static double somaDosElementos(float[] array) {
		double soma = 0;
		for (float i : array)
		{
			soma += i;
		}
		return soma;
	}

	private static double somaDoQuadradoDosElementos(float[] array) {
		double somaDosQuadrados = 0;
		for (float i : array)
		{
			somaDosQuadrados += Math.pow(i, 2);
		}
		return somaDosQuadrados;
	}
	
	public static ArrayList<Entry> getValidWindow(ArrayList<Entry> entries, int minSize)
	{
		float[] array = getArrayFromEntries(entries);
		int size = 0;
		int lastValid = 0;
		float mediana = medianaNaoNula(array);
		array = getArrayFromEntries(entries);
		for (int i = 0; i < array.length; i++)
		{
			if (Math.abs(array[i]-mediana) < mediana*0.2)
			{
				size++;
				lastValid = i;
			}
			else if (size >= minSize)
			{
				break;
			}
			else
			{
				size = 0;
			}
		}
		if (size >= minSize){
			return new ArrayList<Entry>(entries.subList(lastValid - size + 1, lastValid));
		}
		else{
			return null;
		}
	}
	
	private static float mediaAritmetica(float[] array) {
        double total = 0;
        for (int counter = 0; counter < array.length; counter++){
              total += array[counter];
        }
        return (float) (total / array.length);
	}
	
	public static ArrayList<Entry> RemoveDC(ArrayList<Entry> array)
	{
		ArrayList<Entry> arrayFiltered = new ArrayList<Entry>(array.size());
		for (Entry e : array)
		{
			arrayFiltered.add(new Entry(e.getVal(), e.getXIndex()));
		}
		
		float media = mediaAritmetica(getArrayFromEntries(arrayFiltered));
		for (int i = 0; i < arrayFiltered.size(); i++)
		{
			arrayFiltered.get(i).setVal(arrayFiltered.get(i).getVal() - media);
		}
		return arrayFiltered;
	}
	
	public static float[] DFT(ArrayList<Entry> filtered)
	{
		return DFT(getArrayFromEntries(filtered));
		
	}
	
	private static float[] DFT(float[] array) {
		double M = (1.0 / ((10.0/1000.0) * 0.1));
		double dw = (2.0 * Math.PI / M);

		/* ............ DFT ................ */
		/* DC value dealt with separately */

		int TAMY =  (int) (20/0.1);
		float[] yre = new float[TAMY];
		float[] yim = new float[TAMY];
		float[] yMag = new float[TAMY];
		for (int k = 1; k < TAMY; k++) {
			yre[k] = 0;
			yim[k] = 0;
			for (int n = 0; n < array.length; n++) {
				yre[k] += array[n] * Math.cos(k * dw * n);
				yim[k] -= array[n] * Math.sin(k * dw * n);
			}
			yMag[k] = (float) Math.sqrt(Math.pow(yre[k], 2) + Math.pow(yim[k], 2));
		}
		return yMag;
	}
}
