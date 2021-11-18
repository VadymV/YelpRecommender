One of my first ML Projects. It was done in 2016.
It is also very peculiar, because the use of a local hadoop cluster was a requirement.
See 'paper.pdf' for more information. Sorry only in German.

Instructions how to run the recommendation system from scratch.
All commands are for a Linux system.
Characters that follow the symbol # are the comments.
Skip the stage 1 if you want to try out the application.

#########################
#########################
Stage 1. Analytics Engine
#########################
#########################

##########################################
Real Hadoop System. 16 GB RAM is required.
##########################################

0. Get Yelp Data.
	0.1. Folder data_yelp. 

1. Setup.
	1.1. Install docker: https://www.docker.com/
	1.2. Command: docker pull cloudera/quickstart:latest
	1.3. Command: docker images #note the hash of the image and substitute it in 1.4.
	1.4. Command: docker run --privileged=true --hostname=quickstart.cloudera -t -i -p 8888:8888 -p 80:80 cloudera/quickstart /usr/bin/docker-quickstart #if defaults are accepted.
	1.5. Browse hue at localhost:8888. Of course, in your browser.
		1.5.1. Open File Browser.
		1.5.2. Upload file review.json into the directory "cloudera".
		1.5.3. Upload file business.json into the directory "cloudera".
		1.5.4. Upload files located in the folder stopwords, the DVD, into the directory "cloudera".

2. Analysis
	2.1. Run the command: spark-shell
	2.2. Copy and paste commands from the file Analysis.scala one by one if necessary.

3. Copy the analyzed file from CDH5 to your system.

##########################################################
Physical Cores of the CPU are used as nodes of the cluster.
##########################################################

0. Install JAVA.
1. Download Spark.
2. Extract files.
3. Go to the directory bin.
4. Run in your terminal: export  _JAVA_OPTIONS: -Xmx4096m
5. Run in your terminal: ./spark-shell --driver-memory 1536m --executor-memory 1536m
6. Copy and paste commands from the file Analysis.scala one by one if necessary. Please provide appropriate paths for the required files.
7. The analyzed file will be saved as soon as the last command will be executed.

####################
####################
Stage 2. Application
####################
####################

0.Installation
	0.1. Install MongoDB.
	0.2. Create directory, command: sudo mkdir -p /var/lib/mongodb/data/db
	0.2. Run MongoDB server, commad: sudo mongod --dbpath /var/lib/mongodb/data/db
	0.3. Install Java 8.
	0.4. Install Eclipse with Maven support.

1. Setup
	1.1. Command: mongoimport --db yelp --collection restaurants --drop --file "/pathToFile/restaurant.json". The analysed file from the stage 1.
	1.1. Command: mongoimport --db yelp --collection shopping --drop --file "/pathToFile/shopping.json". The analysed file from the stage 1.
	1.1. Command: mongoimport --db yelp --collection health --drop --file "/pathToFile/health.json". The analysed file from the stage 1.

2. Running the application
	2.1. Extract "Anwendung.zip" and import the project "yelp".
	2.2. Run as Java Application.
	2.3. Follow the instructions.