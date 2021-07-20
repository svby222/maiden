# maiden

## Configuring

Example `maiden.conf`:

```hocon
maiden.core {
  ownerId = 999999999
  embedColor = "#ffffff"

  discord.botToken = "[PLACEHOLDER]"

  reddit.clientId = "[PLACEHOLDER]"
  reddit.clientSecret = "[PLACEHOLDER]"

  database.connectionString = "jdbc:postgresql://localhost/"
  database.username = "postgres"
  database.password = ""
}
```

## Implementation

### Reddit module

The main functionality of the Reddit module is implemented separately as part of a Python subproject (`praw_wrapper/`).
This was done primarily because there currently is no up-to-date API wrapper for the JVM.

This subproject is invoked by the bot via the `ProcessBuilder` API.

**This module requires Python 3 as well as [PRAW](https://pypi.org/project/praw/) to be installed in order to function
properly**.
