# Getting started

```
// Run the tests.
sbt test 

// Compile and generate the starting shell script.
sbt stage

// Run the application.
target/universal/stage/bin/gemini-jobcoin-scala
```

Once started, the Jobcoin mixer doesn't expect a console input from the user to start mixing coins. Instead, it assumes that [any new transaction](https://jobcoin.gemini.com/genetics) to one of the mixer wallets (_Mixer[1-10]_) is eligible for mixing. The mixed coins are then sent to three new wallets appended with _-Hidden[1-3]_ such as _Robert-Hidden1_.

For example:
* Create 50 coins for a new wallet, let's say John. Mixer wallets already exist.
* Start the application.
* Send 40 coins to Mixer8.
* Wait a little bit.
* Check that John-Hidden1, John-Hidden2 and John-Hidden3 sum up to ~36.

# Jobcoin Mixer

The mixing mechanism works as follows:
* The application goes through all the transactions for all the mixer wallets.
* Each incoming transaction that has never been processed is added to a list.
* That list is iterated through to begin the mixing process.
* For each entry;
  * the balances for all the mixer wallets are rebalanced to ensure some sort of shuffling.
  * random amounts from random mixer wallets are transferred to the three hidden wallets.
  * a 10% fee is taken.

The idea is to have as many customers as possible, as well as creating as many hidden wallets as possible. While creating a lot of transactions makes the exploration of the blockchain more tedious and slow, it is not impossible to quickly identify customer, mixer and hidden wallets. However, it is my understanding that the difficulty increases exponentially when trying to group hidden wallets to match them to customer wallets. Hence the assumption that the service be efficient with a large client base.

Caveats:
* When the application starts, it ignores all the previous incoming transactions. It won't resume mixing operations.
* In addition to the fee, a very small percentage of the amount is kept by the mixer due to a loss of precision happening somewhere (< 0.0001).

# Improvements

* Add intervals of time in between transactions from the mixer wallets to the hidden wallets. That would make the task of matching hidden wallets with customer wallets harder as the distributed amount would not be equal to the amount sent to the mixer.
* Separate the deposit address from the mixer by using an exchange. I suppose Jobcoin is listed everywhere :)
* Add way more tests. 
* Specifying the libraries that are actually used instead of the Play bundle.
* Use a database to keep track of the transactions.

