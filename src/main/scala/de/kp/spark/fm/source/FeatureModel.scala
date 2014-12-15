package de.kp.spark.fm.source
/* Copyright (c) 2014 Dr. Krusche & Partner PartG
* 
* This file is part of the Spark-FM project
* (https://github.com/skrusche63/spark-fm).
* 
* Spark-FM is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* 
* Spark-FM is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* Spark-FM. 
* 
* If not, see <http://www.gnu.org/licenses/>.
*/

import org.apache.spark._
import org.apache.spark.SparkContext._

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import de.kp.spark.core.model._

import de.kp.spark.fm.{Configuration,SparseVector}
import de.kp.spark.fm.spec.{Fields}

import scala.collection.mutable.{ArrayBuffer,WrappedArray}

class FeatureModel(@transient sc:SparkContext) extends Serializable {
  
  def buildElastic(req:ServiceRequest,rawset:RDD[Map[String,String]],partitions:Int):RDD[(Int,(Double,SparseVector))] = {
    
    val spec = sc.broadcast(Fields.get(req))
    val num_partitions = sc.broadcast(partitions)
        
    val randomizedDS = rawset.map(data => {
      
      val fields = spec.value
      val ix = new java.util.Random().nextInt(num_partitions.value)
 
      val target = data(fields.head).toDouble
      val features = ArrayBuffer.empty[Double]
      
      for (field <- fields.tail) {
        features += data(field).toDouble
      }

      (ix, (target,buildSparseVector(features.toArray)))
      
    })
    
    val partitioner = new Partitioner() {
      
      def numPartitions = num_partitions.value
      def getPartition(key: Any) = key.asInstanceOf[Int]
      
    }
    
    randomizedDS.partitionBy(partitioner)

  }
  
  def buildFile(req:ServiceRequest,rawset:RDD[String],partitions:Int):RDD[(Int,(Double,SparseVector))] = {

    val num_partitions = sc.broadcast(partitions)
    val randomizedDS = rawset.map(line => {
    
      val ix = new java.util.Random().nextInt(num_partitions.value)
      
      val parts = line.split(',')
      
      val target   = parts(0).toDouble
      val features = parts(1).trim().split(' ').map(_.toDouble)

      (ix, (target,buildSparseVector(features)))
   
    })
    
    val partitioner = new Partitioner() {
      
      def numPartitions = num_partitions.value
      def getPartition(key: Any) = key.asInstanceOf[Int]
      
    }
    
    randomizedDS.partitionBy(partitioner)
    
  }
  
  def buildJDBC(req:ServiceRequest,rawset:RDD[Map[String,Any]],partitions:Int):RDD[(Int,(Double,SparseVector))] = {

    val spec = sc.broadcast(Fields.get(req))
    val num_partitions = sc.broadcast(partitions)
    
    val randomizedDS = rawset.map(data => {
      
      val fields = spec.value
      val ix = new java.util.Random().nextInt(num_partitions.value)

      val target = data(fields.head).asInstanceOf[Double]
      val features = ArrayBuffer.empty[Double]
      
      for (field <- fields.tail) {
        features += data(field).asInstanceOf[Double]
      }
 
      (ix, (target,buildSparseVector(features.toArray)))
      
    })
    
    val partitioner = new Partitioner() {
      
      def numPartitions = num_partitions.value
      def getPartition(key: Any) = key.asInstanceOf[Int]
      
    }
    
    randomizedDS.partitionBy(partitioner)
    
  }
  
  def buildParquet(req:ServiceRequest,rawset:RDD[Map[String,Any]],partitions:Int):RDD[(Int,(Double,SparseVector))] = {

    val spec = sc.broadcast(Fields.get(req))
    val num_partitions = sc.broadcast(partitions)
    
    val randomizedDS = rawset.map(data => {
      
      val fields = spec.value
      val ix = new java.util.Random().nextInt(num_partitions.value)
      /*
       * The parquet file can be described in two different ways:
       * 
       * (a) the feature part is specified as an array of values
       * 
       * (b) the feature part is specified by individual fields;
       * in this case the field value must be Doubles
       * 
       */
      if (data.size == 2) {
        /*
         * 'data' specifies a map of two entries, where the first represents the 
         * target variable and the second one the predictor or feature variables
         * as an array; this parquet file format is used, if it specifies by a 
         * targeted point
         */
        val seq = data.toSeq
        
        val target = seq(0)._2.asInstanceOf[Double]
        val features = seq(1)._2.asInstanceOf[WrappedArray[Double]]
 
        (ix, (target,buildSparseVector(features.toArray)))
        
      } else {
      
        val target = data(fields.head).asInstanceOf[Double]
        val features = ArrayBuffer.empty[Double]
      
        for (field <- fields.tail) {
          features += data(field).asInstanceOf[Double]
        }
 
        (ix, (target,buildSparseVector(features.toArray)))
      
      }
      
    })
    
    val partitioner = new Partitioner() {
      
      def numPartitions = num_partitions.value
      def getPartition(key: Any) = key.asInstanceOf[Int]
      
    }
    
    randomizedDS.partitionBy(partitioner)
    
  }

  /**
   * This is a helper method to build a sparse vector from the input data;
   * to this end, we reduce to such entries that are different from zero
   */
  private def buildSparseVector(feature:Array[Double]):SparseVector = {
    
    val vector = new SparseVector(feature.length)
    
    for (i <- 0 until feature.length) {
    	
      val array_i: Double = feature(i)
      if (array_i > 0) vector.update(i, array_i)
        
    }
    
    vector
  
  }

}