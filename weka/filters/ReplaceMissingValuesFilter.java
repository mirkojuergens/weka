/*
 *    ReplaceMissingValuesFilter.java
 *    Copyright (C) 1999 Eibe Frank
 *
 */


package weka.filters;

import java.io.*;
import java.util.*;
import weka.core.*;

/** 
 * Replaces all missing values for nominal and numeric attributes in a 
 * dataset with the modes and means from the training data.
 *
 * @author Eibe Frank (eibe@cs.waikato.ac.nz) 
 * @version $Revision: 1.9 $
 */
public class ReplaceMissingValuesFilter extends Filter {

  /** The modes and means */
  private double[] m_ModesAndMeans = null;

  /**
   * Sets the format of the input instances.
   *
   * @param instanceInfo an Instances object containing the input 
   * instance structure (any instances contained in the object are 
   * ignored - only the structure is required).
   * @return true if the outputFormat may be collected immediately
   * @exception Exception if the input format can't be set 
   * successfully
   */
  public boolean inputFormat(Instances instanceInfo) 
       throws Exception {

    super.inputFormat(instanceInfo);
    setOutputFormat(instanceInfo);
    m_ModesAndMeans = null;
    return true;
  }

  /**
   * Input an instance for filtering. Filter requires all
   * training instances be read before producing output.
   *
   * @param instance the input instance
   * @return true if the filtered instance may now be
   * collected with output().
   * @exception Exception if the input instance was not of the 
   * correct format or if there was a problem with the filtering.
   */
  public boolean input(Instance instance) throws Exception {

    if (getInputFormat() == null) {
      throw new Exception("No input instance format defined");
    }
    if (m_NewBatch) {
      resetQueue();
      m_NewBatch = false;
    }
    if (m_ModesAndMeans == null) {
      bufferInput(instance);
      return false;
    } else {
      convertInstance(instance);
      return true;
    }
  }

  /**
   * Signify that this batch of input to the filter is finished. 
   * If the filter requires all instances prior to filtering,
   * output() may now be called to retrieve the filtered instances.
   *
   * @return true if there are instances pending output
   * @exception Exception if no input structure has been defined
   */
  public boolean batchFinished() throws Exception {

    if (getInputFormat() == null) {
      throw new Exception("No input instance format defined");
    }

    if (m_ModesAndMeans == null) {
      // Compute modes and means
      double sumOfWeights =  getInputFormat().sumOfWeights();
      double[][] counts = new double[getInputFormat().numAttributes()][];
      for (int i = 0; i < getInputFormat().numAttributes(); i++) {
	if (getInputFormat().attribute(i).isNominal()) {
	  counts[i] = new double[getInputFormat().attribute(i).numValues()];
	  counts[i][0] = sumOfWeights;
	}
      }
      double[] sums = new double[getInputFormat().numAttributes()];
      for (int i = 0; i < sums.length; i++) {
	sums[i] = sumOfWeights;
      }
      double[] results = new double[getInputFormat().numAttributes()];
      for (int j = 0; j < getInputFormat().numInstances(); j++) {
	Instance inst = getInputFormat().instance(j);
	for (int i = 0; i < inst.numValues(); i++) {
	  if (!inst.isMissingSparse(i)) {
	    double value = inst.valueSparse(i);
	    if (inst.attributeSparse(i).isNominal()) {
	      counts[inst.index(i)][(int)value] += inst.weight();
	      counts[inst.index(i)][0] -= inst.weight();
	    } else if (inst.attributeSparse(i).isNumeric()) {
	      results[inst.index(i)] += inst.weight() * inst.valueSparse(i);
	    }
	  } else {
	    if (inst.attributeSparse(i).isNominal()) {
	      counts[inst.index(i)][0] -= inst.weight();
	    } else if (inst.attributeSparse(i).isNumeric()) {
	      sums[inst.index(i)] -= inst.weight();
	    }
	  }
	}
      }
      m_ModesAndMeans = new double[getInputFormat().numAttributes()];
      for (int i = 0; i < getInputFormat().numAttributes(); i++) {
	if (getInputFormat().attribute(i).isNominal()) {
	  m_ModesAndMeans[i] = (double)Utils.maxIndex(counts[i]);
	} else if (getInputFormat().attribute(i).isNumeric()) {
	  if (Utils.gr(sums[i], 0)) {
	    m_ModesAndMeans[i] = results[i] / sums[i];
	  }
	}
      }

      // Convert pending input instances
      for(int i = 0; i < getInputFormat().numInstances(); i++) {
	convertInstance(getInputFormat().instance(i));
      }
    } 
    // Free memory
    flushInput();

    m_NewBatch = true;
    return (numPendingOutput() != 0);
  }

  /**
   * Convert a single instance over. The converted instance is 
   * added to the end of the output queue.
   *
   * @param instance the instance to convert
   */
  private void convertInstance(Instance instance) throws Exception {
  
    Instance inst = null;
    if (instance instanceof SparseInstance) {
      double []vals = new double[instance.numValues()];
      int []indices = new int[instance.numValues()];
      int num = 0;
      for (int j = 0; j < instance.numValues(); j++) {
	if (instance.isMissingSparse(j) &&
	    (instance.attributeSparse(j).isNominal() ||
	     instance.attributeSparse(j).isNumeric())) {
	  if (m_ModesAndMeans[instance.index(j)] != 0.0) {
	    vals[num] = m_ModesAndMeans[instance.index(j)];
	    indices[num] = instance.index(j);
	    num++;
	  } 
	} else {
	  vals[num] = instance.valueSparse(j);
	  indices[num] = instance.index(j);
	  num++;
	}
      } 
      if (num == instance.numValues()) {
	inst = new SparseInstance(instance.weight(), vals, indices,
                                  instance.numAttributes());
      } else {
	double []tempVals = new double[num];
	int []tempInd = new int[num];
	System.arraycopy(vals, 0, tempVals, 0, num);
	System.arraycopy(indices, 0, tempInd, 0, num);
	inst = new SparseInstance(instance.weight(), tempVals, tempInd,
                                  instance.numAttributes());
      }
    } else {
      double []vals = new double[getInputFormat().numAttributes()];
      for (int j = 0; j < instance.numAttributes(); j++) {
	if (instance.isMissing(j) &&
	    (getInputFormat().attribute(j).isNominal() ||
	     getInputFormat().attribute(j).isNumeric())) {
	  vals[j] = m_ModesAndMeans[j]; 
	} else {
	  vals[j] = instance.value(j);
	}
      } 
      inst = new Instance(instance.weight(), vals);
    } 
    inst.setDataset(instance.dataset());
    push(inst);
  }

  /**
   * Main method for testing this class.
   *
   * @param argv should contain arguments to the filter: 
   * use -h for help
   */
  public static void main(String [] argv) {

    try {
      if (Utils.getFlag('b', argv)) {
 	Filter.batchFilterFile(new ReplaceMissingValuesFilter(), argv);
      } else {
	Filter.filterFile(new ReplaceMissingValuesFilter(), argv);
      }
    } catch (Exception ex) {
      System.out.println(ex.getMessage());
    }
  }
}








