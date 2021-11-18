/*
* Inferring Topics from reviews. LDA Algorithm.
* Execute these commands in spark-shell.
*/

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.sql.functions._
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.hive.HiveContext
import org.apache.spark.sql.functions.udf
import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.feature.HashingTF
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.sql.types._
import org.apache.spark.sql.DataFrame
import org.apache.spark.ml.feature.{CountVectorizer, RegexTokenizer, StopWordsRemover}
import org.apache.spark.mllib.clustering.{LDA, EMLDAOptimizer, DistributedLDAModel}
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.sql._
import org.apache.spark.ml.feature.StringIndexer
import org.apache.spark.storage.StorageLevel._
import org.apache.spark.sql.functions.{collect_list, udf, lit}
import scala.collection.mutable.WrappedArray
import sqlContext.implicits._

// Please enter here: Restaurants, Health or Shopping
val category: String = "Restaurants"

//------------------------Local Functions------------------------------------

//Makes String from the array.
val mkString = udf((arrayCol: Seq[String]) => arrayCol.mkString(","))

//Makes String from the array.
val mkStringFromInteger = udf((arrayCol: Seq[Int]) => arrayCol.mkString(","))

//Makes String from the array.
val mkStringFromDouble = udf((arrayCol: Seq[Double]) => arrayCol.mkString(","))

//Converts to Long.
val toLong = udf[Long, Double](_.toLong)

val removePunctuation = udf((xs: String) => xs.toLowerCase.replaceAll("[^a-zA-Z\\s]", ""))

val combineArrays = udf((xs: WrappedArray[Long], xy: WrappedArray[Double]) => xs zip xy)

val toMapUDF = udf((x: WrappedArray[(Long, Double)]) => x.toMap)

//Converts to Long.
val toDouble = udf[Double, String](_.toDouble)


//------------------------Prepare Data---------------------------------------

//Data.
val businessOrig = sqlContext.read.json("/user/cloudera/business.json")
val selectedCategoryOrig = businessOrig.select("business_id", "categories", "city", "name", "full_address", "review_count", "stars", "longitude", "latitude").withColumn("categories", mkString(businessOrig("categories")))
val selectedCategory = selectedCategoryOrig.filter(selectedCategoryOrig("categories").contains (category))

//selectedCategory's ids in category Restaurants.
val selectedCategory_id = selectedCategory.select("business_id")

//Review Data
val reviewOrig = sqlContext.read.json("/user/cloudera/review.json")
val review_data = reviewOrig.select("business_id", "text")
val indexer = new StringIndexer().setInputCol("business_id").setOutputCol("business_index")
val indexed = indexer.fit(review_data).transform(review_data)
val review = indexed.withColumn("business_index", toLong(indexed("business_index")))

//Stopwords.
val linesEN = sc.textFile("/user/cloudera/stopwords_en.txt").toLocalIterator.toArray
val linesDE = sc.textFile("/user/cloudera/stopwords_de.txt").toLocalIterator.toArray
val linesFR = sc.textFile("/user/cloudera/stopwords_fr.txt").toLocalIterator.toArray
val syllableadjectivesOne = sc.textFile("/user/cloudera/1syllableadjectives.txt").toLocalIterator.toArray
val syllableadjectivesTwo = sc.textFile("/user/cloudera/1syllableadjectives.txt").toLocalIterator.toArray
val syllableadjectivesThree = sc.textFile("/user/cloudera/1syllableadjectives.txt").toLocalIterator.toArray
val syllableadjectivesFour = sc.textFile("/user/cloudera/1syllableadjectives.txt").toLocalIterator.toArray
val syllableadverbsOne = sc.textFile("/user/cloudera/1syllableadverbs.txt").toLocalIterator.toArray
val syllableadverbsTwo = sc.textFile("/user/cloudera/1syllableadverbs.txt").toLocalIterator.toArray
val syllableadverbsThree = sc.textFile("/user/cloudera/1syllableadverbs.txt").toLocalIterator.toArray
val syllableadverbsFour = sc.textFile("/user/cloudera/1syllableadverbs.txt").toLocalIterator.toArray
val syllableverbsOne = sc.textFile("/user/cloudera/1syllableverbs.txt").toLocalIterator.toArray
val syllableverbsTwo = sc.textFile("/user/cloudera/1syllableverbs.txt").toLocalIterator.toArray
val syllableverbsThree = sc.textFile("/user/cloudera/1syllableverbs.txt").toLocalIterator.toArray
val syllableverbsFour = sc.textFile("/user/cloudera/1syllableverbs.txt").toLocalIterator.toArray

val stopWords = linesEN ++ linesDE ++ linesFR ++ syllableadjectivesOne ++ syllableadjectivesTwo ++ syllableadjectivesThree ++ syllableadjectivesFour ++ syllableadverbsOne ++ syllableadverbsTwo ++ syllableadverbsThree ++ syllableadverbsFour ++ syllableverbsOne ++ syllableverbsTwo ++ syllableverbsThree ++ syllableverbsFour

//Reviews for category selectedCategory.
val review_selectedCategory_temp = review.join(selectedCategory_id, "business_id")
val review_selectedCategory_grouped = review_selectedCategory_temp.groupBy($"business_id", $"business_index").agg(concat_ws(" ", collect_list($"text")).alias("text"))
val review_selectedCategory = review_selectedCategory_grouped.withColumn("text", removePunctuation(review_selectedCategory_grouped("text")))

//Create tokens from reviews.
val tokens = new RegexTokenizer().setInputCol("text").setOutputCol("words").setMinTokenLength(3).setGaps(true).transform(review_selectedCategory)
val token = new StopWordsRemover().setStopWords(stopWords).setCaseSensitive(false).setInputCol("words").setOutputCol("filtered").transform(tokens)
val review_filtered = token.where(size(col("filtered")) > 5)
val review_selected = review_filtered.select("business_id", "business_index", "filtered")
val review_per_business = review.select("business_id", "business_index")

//---------------------------------LDA---------------------------------------

//Set up.
val numTopics: Int = 30
val maxIterations: Int = 50
val vocabSize: Int = 10000

val cvModel = new CountVectorizer().setInputCol("filtered").setOutputCol("features").setVocabSize(vocabSize).fit(review_selected)

val countVectors = cvModel.transform(review_selected).select("business_index", "features").map { case Row(business_index: Long, countVector: Vector) => (business_index, countVector) }.cache()

val lda = new LDA().setOptimizer(new EMLDAOptimizer()).setK(numTopics).setMaxIterations(maxIterations).setDocConcentration(-1).setTopicConcentration(-1)

val startTime = System.nanoTime()
val ldaModel = lda.run(countVectors)
val elapsed = ((System.nanoTime() - startTime) / 1e9) / 60

// Print the topics, showing the top-weighted terms for each topic.
val topicIndices = ldaModel.describeTopics(maxTermsPerTopic = 15)
val vocabArray = cvModel.vocabulary
val topics = topicIndices.map { case (terms, termWeights) =>
 terms.map(vocabArray(_)).zip(termWeights)
}

//Topics will be printed
println(s"$numTopics topics:")
topics.zipWithIndex.foreach { case (topic, i) =>
 println(s"TOPIC $i")
 topic.foreach { case (term, weight) => println(s"$term\t$weight") }
 println(s"==========")
}

ldaModel.save(sc, "/user/cloudera/LDAModel")
val sameModel = DistributedLDAModel.load(sc, "/user/cloudera/LDAModel")

//Recommendation list
val documents = sameModel.topTopicsPerDocument(1)
val temp_topic1 = documents.toDF()
val temp_topic2 = temp_topic1.withColumn("_2", mkStringFromInteger(temp_topic1("_2")))
val topic_business1 = temp_topic2.withColumn("_3", mkStringFromDouble(temp_topic2("_3")))
val topic_business = topic_business1.selectExpr("_1 as business_index", "_2 as topic", "_3 as probability")
val topic_per_business = review_per_business.join(topic_business, "business_index")

val final_set_temp = selectedCategory.join(topic_per_business, "business_id")
val final_set = final_set_temp.select("business_id", "city", "name", "full_address", "stars", "probability", "topic", "categories", "longitude", "latitude")
val final_business = final_set.withColumn("probability", toDouble(final_set("probability")))
val final_data = final_business.distinct()

//Analysed data will be saved.
final_data.write.json("/user/cloudera/result")
