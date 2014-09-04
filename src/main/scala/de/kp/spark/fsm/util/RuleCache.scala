package de.kp.spark.fsm.util
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

import de.kp.spark.fsm.{Configuration,FSMRule}
import java.util.Date

object RuleCache {
  
  private val maxentries = Configuration.cache  
  private val cache = new LRUCache[(String,Long),List[FSMRule]](maxentries)

  def add(uid:String,rules:List[FSMRule]) {
   
    val now = new Date()
    val timestamp = now.getTime()
    
    val k = (uid,timestamp)
    val v = rules
    
    cache.put(k,v)
    
  }
  
  def exists(uid:String):Boolean = {
    
    val keys = cache.keys().filter(key => key._1 == uid)
    (keys.size > 0)
    
  }
  
  def rules(uid:String):List[FSMRule] = {
    
    val keys = cache.keys().filter(key => key._1 == uid)
    if (keys.size == 0) {    
      null
      
    } else {
      
      val last = keys.sortBy(_._2).last
      cache.get(last) match {
        
        case None => null
        case Some(rules) => rules
      
      }
      
    }
  
  }

  /**
   * Retrieve those rules, where the antecedents match
   * the provided ones, and restrict to those consequents
   * that have the maximum confidence
   */
  def consequent(uid:String,antecedent:List[Int]):List[Int] = {
  
    /* Restrict to those rules, that match the antecedents */
    val candidates = rules(uid)
      .filter(rule => isEqual(rule.antecedent,antecedent))
      .map(rule => (rule.consequent,rule.confidence,rule.support))
      
    if (candidates.isEmpty) {
      List.empty[Int]
    
    } else
      candidates.sortBy(_._2).reverse.head._1

  } 
  
  private def isEqual(itemset1:List[Int],itemset2:List[Int]):Boolean = {
    
    if (itemset1.length != itemset2.length) {
      return false
    }
    var sum:Int = 0
    (0 until itemset1.length).foreach(i => {
      sum += Math.abs(itemset1(i) - itemset2(i))
    })
    
    (sum == 0)
    
  }
}