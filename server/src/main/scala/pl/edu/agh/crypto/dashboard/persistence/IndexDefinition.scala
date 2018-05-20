package pl.edu.agh.crypto.dashboard.persistence

//we are assuming all needed indexes will be skip-list, for fast range lookup
case class IndexDefinition(field: String, unique: Boolean)
