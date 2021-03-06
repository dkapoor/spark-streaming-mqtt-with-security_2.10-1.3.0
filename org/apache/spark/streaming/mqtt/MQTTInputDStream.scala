/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.streaming.mqtt

import scala.collection.Map
import scala.collection.mutable.HashMap
import scala.collection.JavaConversions._
import scala.reflect.ClassTag

import java.util.Properties
import java.util.concurrent.Executors
import java.io.IOException
import javax.net.ssl.SSLContext;

import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttClientPersistence
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.MqttTopic
import org.eclipse.paho.client.mqttv3.MqttConnectOptions

import org.apache.spark.Logging
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream._
import org.apache.spark.streaming.receiver.Receiver

/**
 * Input stream that subscribe messages from a Mqtt Broker.
 * Uses eclipse paho as MqttClient http://www.eclipse.org/paho/
 * @param brokerUrl Url of remote mqtt publisher
 * @param topic topic name to subscribe to
 * @param storageLevel RDD storage level.
 */

private[streaming]
class MQTTInputDStream(
    @transient ssc_ : StreamingContext,
    brokerUrl: String,
    topic: String,
    storageLevel: StorageLevel,
    clientID: String,
    userName: String,
    password: String
  ) extends ReceiverInputDStream[String](ssc_) {

  def getReceiver(): Receiver[String] = {
    new MQTTReceiver(brokerUrl, topic, storageLevel, clientID, userName, password)
  }
}

private[streaming]
class MQTTReceiver(
    brokerUrl: String,
    topic: String,
    storageLevel: StorageLevel,
    clientID: String,
    userName: String,
    password: String
  ) extends Receiver[String](storageLevel) {

  def onStop() {

  }

  def onStart() {

    // Set up persistence for messages
    val persistence = new MemoryPersistence()
    var clientId = clientID;
    if(clientId == null) {
    	clientId = MqttClient.generateClientId()
    }
	
    // Initializing Mqtt Client specifying brokerUrl, clientID and MqttClientPersistance
    val client = new MqttClient(brokerUrl, clientId, persistence)

    val connectOptions = new MqttConnectOptions()
    if(userName != null && password != null) {
    	// Create a secure connection
    	connectOptions.setUserName(userName)
    	connectOptions.setPassword(password.toCharArray())
      if(brokerUrl.indexOf("ssl") == 0) {
      	val sslContext = SSLContext.getInstance("TLSv1.2");
      	sslContext.init(null, null, null);
      	connectOptions.setSocketFactory(sslContext.getSocketFactory());
      }
    }

    // Callback automatically triggers as and when new message arrives on specified topic
    val callback: MqttCallback = new MqttCallback() {
    	// Handles Mqtt message
    	override def messageArrived(arg0: String, arg1: MqttMessage) {
    	    store(arg0 + " "+ new String(arg1.getPayload(),"utf-8"))
    	}

      override def deliveryComplete(arg0: IMqttDeliveryToken) {
      	
      }

      override def connectionLost(arg0: Throwable) {
          restart("Connection lost ", arg0)
      }
    }

    // Set up callback for MqttClient. This needs to happen before
    // connecting or subscribing, otherwise messages may be lost
    client.setCallback(callback)

    // Connect to MqttBroker
    client.connect(connectOptions)

    // Subscribe to Mqtt topic
    client.subscribe(topic)

  }
}

