# Real Time Game Recommender System
## A Scala Bootcamp course project

This recommender system reads finished game events from Kafka and creates gametype recommendations for currently active players. The recommendations are available over Http4s and the internal state is kept in memory and also persisted in DynamoDb so that multiple instances can be used for processing.
