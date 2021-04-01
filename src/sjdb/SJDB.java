/**
 * 
 */
package sjdb;

import java.io.*;

/**
 * @author nmg
 *
 */
public class SJDB {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		// read serialised catalogue from file and parse
		String catFile = "data/cat.txt";
		Catalogue cat = new Catalogue();
		CatalogueParser catParser = new CatalogueParser(catFile, cat);
		catParser.parse();

		// read stdin, parse, and build canonical query plan
		QueryParser queryParser = new QueryParser(cat, new FileReader(new File("data/q5.txt")));
		Operator plan = queryParser.parse();

		// create estimator visitor and apply it to canonical plan
		Estimator est1 = new Estimator();
		plan.accept(est1);
		System.out.println("canonical plan: " + plan);
		System.out.println("canonical plan cost: " + est1.getCost());

		// create optimised plan
		Optimiser opt = new Optimiser(cat);
		Operator optPlan = opt.optimise(plan);
		Estimator est2 = new Estimator();
		optPlan.accept(est2);
		System.out.println("optimised plan: " + optPlan);
		System.out.print("optimised plan cost: " + est2.getCost());
	}

}
