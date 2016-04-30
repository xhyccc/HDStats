package edu.uva.hdstats;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.UUID;

import Jama.Matrix;

public class GLassoEstimator extends LDEstimator {
	private double _lambda = 0.01;

	public GLassoEstimator() {
	}

	public GLassoEstimator(double lambda) {
		this._lambda = lambda;
	}

	@Override
	public double[][] covariance(double[][] samples) {
		double[][] precision_matrix = _glassoGetInverseCovarianceMatrix(super.covariance(samples));
		// covarianceApprox(covar_inner);
		Matrix m = new Matrix(precision_matrix);
		return m.inverse().getArray();
	}

	private double[][] _glassoGetInverseCovarianceMatrix(double[][] covx) {
		String id=UUID.randomUUID().toString();
		double[][] inverseCovarianceMatrix = new double[covx.length][covx.length];
		/// System.out.println("data length:"+data[0].length);
		try {
			PrintWriter writer = new PrintWriter(new FileWriter("R_tmp"+id+".data"));
			// writer.print("variable_0");
			// for(int i = 1; i < covx[0].length; i++)
			// writer.print(",variable_"+i);
			System.out.println(covx[0].length + " x " + covx.length);
			System.out.println("cols," + covx[0].length);

			for (int i = 0; i < covx.length; i++) {
				writer.print(covx[i][0]);
				for (int j = 1; j < covx[i].length; j++) {
					writer.print("," + covx[i][j]);
				}
				writer.print("\n");
				writer.println();
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		try {
			PrintWriter writer = new PrintWriter(new FileWriter("R_tmp"+id+".R"));
			writer.println("library(glasso)");
			writer.println("R_dataset = read.csv(\"R_tmp"+id+".data\", header=FALSE)");
			// writer.println("R_dataset");
			writer.println("R_covarianceMatrix = as.matrix(R_dataset)");
			// writer.println("R_covarianceMatrix[300,600]");
			writer.println("R_glasso = glasso(R_covarianceMatrix, rho="+this._lambda+")");
			// writer.println("R_glasso$wi");
			writer.println("write(t(R_glasso$wi), file=\"R_glasso_wi_tmp"+id+".txt\", "
					+ "ncolumns=dim(R_glasso$wi)[[2]], sep=\",\")");

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		// execute "Rscript R_tmp.R"
		try {
			Process p = Runtime.getRuntime().exec("Rscript R_tmp"+id+".R");

			String s;

			// read the output from the command
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
			// System.out.println("********************************************************");
			// System.out.println("Here is the standard output of the
			// command:\n");
			while ((s = stdInput.readLine()) != null) {
				System.out.println(s);
			}
			// System.out.println("********************************************************");
			// System.out.println();
			stdInput.close();

			// read any errors from the command
			BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
			// System.out.println("********************************************************");
			// System.out.println("Here is the standard error of the command (if
			// any):\n");
			while ((s = stdError.readLine()) != null) {
				System.out.println(s);
			}
			// System.out.println("********************************************************");
			// System.out.println();
			stdError.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		try {
			BufferedReader inputReader = new BufferedReader(new FileReader("R_glasso_wi_tmp"+id+".txt"));
			for (int i = 0; i < inverseCovarianceMatrix.length; i++) {
				String line = inputReader.readLine();
				String[] lns = line.split(",");
				// System.out.println("r size:"+lns.length);
				// System.out.println("r output:"+line);
				// StringTokenizer t = new StringTokenizer(line, "\t");
				for (int j = 0; j < inverseCovarianceMatrix[i].length; j++) {
					inverseCovarianceMatrix[i][j] = Double.parseDouble(lns[j]);
				}
			}
			inputReader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return inverseCovarianceMatrix;
	}

}