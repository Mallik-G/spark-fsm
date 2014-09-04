package de.kp.spark.fsm.source
/* Copyright (c) 2014 Dr. Krusche & Partner PartG
* 
* This file is part of the Spark-FSM project
* (https://github.com/skrusche63/spark-fsm).
* 
* Spark-FSM is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* 
* Spark-FSM is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* Spark-FSM. 
* 
* If not, see <http://www.gnu.org/licenses/>.
*/

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import org.apache.hadoop.conf.{Configuration => HConf}
import org.apache.hadoop.io.{ArrayWritable,MapWritable,NullWritable,Text}

import org.elasticsearch.hadoop.mr.EsInputFormat

import de.kp.spark.fsm.spec.FieldSpec

import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer

class ElasticSource(sc:SparkContext) extends Serializable {
 
  /**
   * Load ecommerce items that refer to a certain site (tenant), user
   * and transaction or order
   */
  def items(conf:HConf):RDD[(Int,Array[String])] = {
     
    val spec = sc.broadcast(FieldSpec.get)

    /* Connect to Elasticsearch */
    val source = sc.newAPIHadoopRDD(conf, classOf[EsInputFormat[Text, MapWritable]], classOf[Text], classOf[MapWritable])
    val dataset = source.map(hit => toMap(hit._2))

    val items = dataset.map(data => {
      
      val site = data(spec.value("site")._1)
      val timestamp = data(spec.value("timestamp")._1).toLong

      val user = data(spec.value("user")._1)      
      val order = data(spec.value("order")._1)

      val item  = data(spec.value("item")._1)
      
      (site,user,order,timestamp,item)
      
    })
    /*
     * Group items by 'order' and aggregate all items of a single order
     * into a single line and repartition ids to single partition
     */
    val ids = items.groupBy(_._3).map(valu => {
      
      /* Sort grouped orders by (ascending) timestamp */
      val data = valu._2.toList.sortBy(_._4)      
      data.map(record => record._5).toArray
       
    }).coalesce(1)

    val index = sc.parallelize(Range.Long(0,ids.count,1),ids.partitions.size)
    ids.zip(index).map(valu => (valu._2.toInt,valu._1)).cache()

  }
  
  /**
   * A helper method to convert a MapWritable into a Map
   */
  private def toMap(mw:MapWritable):Map[String,String] = {
      
    val m = mw.map(e => {
        
      val k = e._1.toString        
      val v = (if (e._2.isInstanceOf[Text]) e._2.toString()
        else if (e._2.isInstanceOf[ArrayWritable]) {
        
          val array = e._2.asInstanceOf[ArrayWritable].get()
          array.map(item => {
            
            (if (item.isInstanceOf[NullWritable]) "" else item.asInstanceOf[Text].toString)}).mkString(",")
            
        }
        else "")
        
    
      k -> v
        
    })
      
    m.toMap
    
  }

}