/*
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * RepeatedHillClimber.java
 * Copyright (C) 2004-2012 University of Waikato, Hamilton, New Zealand
 *
 */

package weka.classifiers.bayes.net.search.global;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Random;
import java.util.Vector;

import weka.classifiers.bayes.BayesNet;
import weka.classifiers.bayes.net.ParentSet;
import weka.core.Instances;
import weka.core.Option;
import weka.core.RevisionUtils;
import weka.core.Utils;

/**
 * <!-- globalinfo-start --> This Bayes Network learning algorithm repeatedly uses hill climbing starting with a randomly generated network structure and return the best structure of the various runs.
 * <p/>
 * <!-- globalinfo-end -->
 *
 * <!-- options-start --> Valid options are:
 * <p/>
 *
 * <pre>
 * -U &lt;integer&gt;
 *  Number of runs
 * </pre>
 *
 * <pre>
 * -A &lt;seed&gt;
 *  Random number seed
 * </pre>
 *
 * <pre>
 * -P &lt;nr of parents&gt;
 *  Maximum number of parents
 * </pre>
 *
 * <pre>
 * -R
 *  Use arc reversal operation.
 *  (default false)
 * </pre>
 *
 * <pre>
 * -N
 *  Initial structure is empty (instead of Naive Bayes)
 * </pre>
 *
 * <pre>
 * -mbc
 *  Applies a Markov Blanket correction to the network structure,
 *  after a network structure is learned. This ensures that all
 *  nodes in the network are part of the Markov blanket of the
 *  classifier node.
 * </pre>
 *
 * <pre>
 * -S [LOO-CV|k-Fold-CV|Cumulative-CV]
 *  Score type (LOO-CV,k-Fold-CV,Cumulative-CV)
 * </pre>
 *
 * <pre>
 * -Q
 *  Use probabilistic or 0/1 scoring.
 *  (default probabilistic scoring)
 * </pre>
 *
 * <!-- options-end -->
 *
 * @author Remco Bouckaert (rrb@xm.co.nz)
 * @version $Revision$
 */
public class RepeatedHillClimber extends HillClimber {

	/** for serialization */
	static final long serialVersionUID = -7359197180460703069L;

	/** number of runs **/
	int m_nRuns = 10;
	/** random number seed **/
	int m_nSeed = 1;
	/** random number generator **/
	Random m_random;

	/**
	 * search determines the network structure/graph of the network with the repeated hill climbing.
	 *
	 * @param bayesNet
	 *            the network to use
	 * @param instances
	 *            the data to use
	 * @throws Exception
	 *             if something goes wrong
	 **/
	@Override
	protected void search(final BayesNet bayesNet, final Instances instances) throws Exception {
		this.m_random = new Random(this.getSeed());
		// keeps track of score pf best structure found so far
		double fBestScore;
		double fCurrentScore = this.calcScore(bayesNet);

		// keeps track of best structure found so far
		BayesNet bestBayesNet;

		// initialize bestBayesNet
		fBestScore = fCurrentScore;
		bestBayesNet = new BayesNet();
		bestBayesNet.m_Instances = instances;
		bestBayesNet.initStructure();
		this.copyParentSets(bestBayesNet, bayesNet);

		// go do the search
		for (int iRun = 0; iRun < this.m_nRuns; iRun++) {
			// XXX interrupt weka
			if (Thread.interrupted()) {
				throw new InterruptedException("Killed WEKA!");
			}
			// generate random nework
			this.generateRandomNet(bayesNet, instances);

			// search
			super.search(bayesNet, instances);

			// calculate score
			fCurrentScore = this.calcScore(bayesNet);

			// keep track of best network seen so far
			if (fCurrentScore > fBestScore) {
				fBestScore = fCurrentScore;
				this.copyParentSets(bestBayesNet, bayesNet);
			}
		}

		// restore current network to best network
		this.copyParentSets(bayesNet, bestBayesNet);

		// free up memory
		bestBayesNet = null;
	} // search

	/**
	 *
	 * @param bayesNet
	 * @param instances
	 * @throws InterruptedException
	 */
	void generateRandomNet(final BayesNet bayesNet, final Instances instances) throws InterruptedException {
		int nNodes = instances.numAttributes();
		// clear network
		for (int iNode = 0; iNode < nNodes; iNode++) {
			ParentSet parentSet = bayesNet.getParentSet(iNode);
			while (parentSet.getNrOfParents() > 0) {
				parentSet.deleteLastParent(instances);
			}
		}

		// initialize as naive Bayes?
		if (this.getInitAsNaiveBayes()) {
			int iClass = instances.classIndex();
			// initialize parent sets to have arrow from classifier node to
			// each of the other nodes
			for (int iNode = 0; iNode < nNodes; iNode++) {
				if (iNode != iClass) {
					bayesNet.getParentSet(iNode).addParent(iClass, instances);
				}
			}
		}

		// insert random arcs
		int nNrOfAttempts = this.m_random.nextInt(nNodes * nNodes);
		for (int iAttempt = 0; iAttempt < nNrOfAttempts; iAttempt++) {
			int iTail = this.m_random.nextInt(nNodes);
			int iHead = this.m_random.nextInt(nNodes);
			if (bayesNet.getParentSet(iHead).getNrOfParents() < this.getMaxNrOfParents() && this.addArcMakesSense(bayesNet, instances, iHead, iTail)) {
				bayesNet.getParentSet(iHead).addParent(iTail, instances);
			}
		}
	} // generateRandomNet

	/**
	 * copyParentSets copies parent sets of source to dest BayesNet
	 *
	 * @param dest
	 *            destination network
	 * @param source
	 *            source network
	 */
	void copyParentSets(final BayesNet dest, final BayesNet source) {
		int nNodes = source.getNrOfNodes();
		// clear parent set first
		for (int iNode = 0; iNode < nNodes; iNode++) {
			dest.getParentSet(iNode).copy(source.getParentSet(iNode));
		}
	} // CopyParentSets

	/**
	 * Returns the number of runs
	 *
	 * @return number of runs
	 */
	public int getRuns() {
		return this.m_nRuns;
	} // getRuns

	/**
	 * Sets the number of runs
	 *
	 * @param nRuns
	 *            The number of runs to set
	 */
	public void setRuns(final int nRuns) {
		this.m_nRuns = nRuns;
	} // setRuns

	/**
	 * Returns the random seed
	 *
	 * @return random number seed
	 */
	public int getSeed() {
		return this.m_nSeed;
	} // getSeed

	/**
	 * Sets the random number seed
	 *
	 * @param nSeed
	 *            The number of the seed to set
	 */
	public void setSeed(final int nSeed) {
		this.m_nSeed = nSeed;
	} // setSeed

	/**
	 * Returns an enumeration describing the available options.
	 *
	 * @return an enumeration of all the available options.
	 */
	@Override
	public Enumeration<Option> listOptions() {
		Vector<Option> newVector = new Vector<Option>(4);

		newVector.addElement(new Option("\tNumber of runs", "U", 1, "-U <integer>"));
		newVector.addElement(new Option("\tRandom number seed", "A", 1, "-A <seed>"));

		newVector.addAll(Collections.list(super.listOptions()));

		return newVector.elements();
	} // listOptions

	/**
	 * Parses a given list of options.
	 * <p/>
	 *
	 * <!-- options-start --> Valid options are:
	 * <p/>
	 *
	 * <pre>
	 * -U &lt;integer&gt;
	 *  Number of runs
	 * </pre>
	 *
	 * <pre>
	 * -A &lt;seed&gt;
	 *  Random number seed
	 * </pre>
	 *
	 * <pre>
	 * -P &lt;nr of parents&gt;
	 *  Maximum number of parents
	 * </pre>
	 *
	 * <pre>
	 * -R
	 *  Use arc reversal operation.
	 *  (default false)
	 * </pre>
	 *
	 * <pre>
	 * -N
	 *  Initial structure is empty (instead of Naive Bayes)
	 * </pre>
	 *
	 * <pre>
	 * -mbc
	 *  Applies a Markov Blanket correction to the network structure,
	 *  after a network structure is learned. This ensures that all
	 *  nodes in the network are part of the Markov blanket of the
	 *  classifier node.
	 * </pre>
	 *
	 * <pre>
	 * -S [LOO-CV|k-Fold-CV|Cumulative-CV]
	 *  Score type (LOO-CV,k-Fold-CV,Cumulative-CV)
	 * </pre>
	 *
	 * <pre>
	 * -Q
	 *  Use probabilistic or 0/1 scoring.
	 *  (default probabilistic scoring)
	 * </pre>
	 *
	 * <!-- options-end -->
	 *
	 * @param options
	 *            the list of options as an array of strings
	 * @throws Exception
	 *             if an option is not supported
	 */
	@Override
	public void setOptions(final String[] options) throws Exception {
		String sRuns = Utils.getOption('U', options);
		if (sRuns.length() != 0) {
			this.setRuns(Integer.parseInt(sRuns));
		}

		String sSeed = Utils.getOption('A', options);
		if (sSeed.length() != 0) {
			this.setSeed(Integer.parseInt(sSeed));
		}

		super.setOptions(options);
	} // setOptions

	/**
	 * Gets the current settings of the search algorithm.
	 *
	 * @return an array of strings suitable for passing to setOptions
	 */
	@Override
	public String[] getOptions() {

		Vector<String> options = new Vector<String>();

		options.add("-U");
		options.add("" + this.getRuns());

		options.add("-A");
		options.add("" + this.getSeed());

		Collections.addAll(options, super.getOptions());

		return options.toArray(new String[0]);
	} // getOptions

	/**
	 * This will return a string describing the classifier.
	 *
	 * @return The string.
	 */
	@Override
	public String globalInfo() {
		return "This Bayes Network learning algorithm repeatedly uses hill climbing starting " + "with a randomly generated network structure and return the best structure of the " + "various runs.";
	} // globalInfo

	/**
	 * @return a string to describe the Runs option.
	 */
	public String runsTipText() {
		return "Sets the number of times hill climbing is performed.";
	} // runsTipText

	/**
	 * @return a string to describe the Seed option.
	 */
	public String seedTipText() {
		return "Initialization value for random number generator." + " Setting the seed allows replicability of experiments.";
	} // seedTipText

	/**
	 * Returns the revision string.
	 *
	 * @return the revision
	 */
	@Override
	public String getRevision() {
		return RevisionUtils.extract("$Revision$");
	}
}
