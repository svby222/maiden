# maiden

## Configuring

Example `maiden.conf`:

```hocon
maiden.core {
  ownerId = 999999999
  embedColor = "#ffffff"

  discord.botToken = "[PLACEHOLDER]"

  database.connectionString = "jdbc:postgresql://localhost/"
  database.username = "postgres"
  database.password = ""
}
```
