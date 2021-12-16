package group27;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map.Entry;

import genius.core.issue.Objective;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.Evaluator;

public class FilePrinter {

	private String filename;
	
	public FilePrinter(String filename) {
		try {
			this.filename = filename;
			FileWriter file = new FileWriter("src/" + filename + ".txt", false);
			BufferedWriter bw = new BufferedWriter(file);
			bw.write("");
			bw.close();
		} catch (IOException e) {
		    e.printStackTrace();
		}    
	}
	
	public void addLine(String line) {
		try {
			FileWriter file = new FileWriter("src/" + filename + ".txt", true);
			BufferedWriter bw = new BufferedWriter(file);
			bw.append(line + "\n");
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void addUtilSpace(AdditiveUtilitySpace utilSpace) {
		String result = "";
		for (Entry<Objective, Evaluator> entry : utilSpace.getfEvaluators().entrySet()) {
			result += (entry.getValue().getWeight() + "=");
			result += (entry.getKey().getName() + ":" + entry.getValue().toString() + "|");
		}
		try {
			FileWriter file = new FileWriter("src/" + filename + ".txt", true);
			BufferedWriter bw = new BufferedWriter(file);
			bw.write(result + "\n");
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
