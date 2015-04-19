# DatabaseLib
Simple Framework that allows an easy access to the SQLite database provided by Android. In order to use correctly and efficiently the database you have to define few classes and the framework will perform all the access t the Android provider, this let you free to think to other stuff instead of the database communications.
_The library is able to design a database with relation 1:n._

#### Main Contributors
 - Samuele Maci (macisamuele@gmail.com)
 - Sergio Schena (sergio.schena@gmail.com)

### Introduction
This work was provided under a development project with the DressMeApp Team ([Official Web Site](http://dressmeappofficial.appspot.com)).
This library was thought to deal with the database access because very often you need to provide a large number of operations (like extracting and parsing) just to can manage the data.
With our library you will reduce all this operations just calling some exposed API, obviously we let you free to perform the _low-level_ operations even through the framework.

### How to use
In order to build a SQLite database with our library you need to:
 - **specialize** the `BaseDatabaseHelper`class: you will define in this class the name of the database file and its version
 - **specialize** the `BaseDatabaseAdapter` class: it will be your access point to all the features provided by the library. You need this specialization in order to _inform_ the Adapter about which database (`DatabaseHelper`) will manage
 - **define** all the model classes that you are going to store into the database. The definition of the model class has some constraints, the class should:
      - have the empty constructor
      - be annotated with the `@Table` annotation in order to assign the model to a given database
      - contains the `Fields` inner-class in order to define all the table's columns (`@Column` annotation)
 - **define** the services classes: in order to keep disjoint model and control of the data, the service classes will be the ones in charge to contatact the database

Anyway in the repository will be available an example of use that will be much more clear :D
