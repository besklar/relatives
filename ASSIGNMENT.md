# Take-home exercise: person browser

Build a small mobile app that lists people from a records service and shows a
profile page for each one.

Pick whatever platform, language, and framework you work best in. Native or
cross-platform is your call. The only requirement is that it runs on a phone,
either a physical device or an emulator or simulator.

We use this exercise to start a conversation, not to score you against a hidden
checklist. Expect to walk us through your code in the next interview.

## How long this should take

Not long. This is meant to be a small, quick build, and **four hours is a hard
ceiling rather than a target**. If you wrap up in an hour or two, that's a good
sign, not a gap. We're not looking for polish, and there are no bonus points
hiding in the corners.

If you run out of time partway through, stop where you are and note it in your
README. "I skipped the offline piece, here's the approach I'd have taken" tells
us more than a half-finished version would, and nobody is marked down for a
short, honest submission.

## What to build

1. **List screen.** Fetch the list of people and show them in a scrollable list.
   Each row shows the person's portrait, full name, lifespan, and birthplace.
2. **Your own model types.** Decode the response into types you define. Your UI
   reads your models, not raw JSON or dictionaries.
3. **Profile screen.** Tapping a row opens that person's profile, fetched from
   the person endpoint. Show the portrait at a larger size, the birth and death
   details, the occupation, the biography, and the list of relatives.
4. **Navigate the family.** Tapping a relative on a profile opens that relative's
   profile.
5. **Works offline.** Anything you have already loaded stays viewable with no
   network. We test this by launching the app online, browsing the list, opening
   two or three profiles, force-quitting the app, turning on airplane mode, and
   relaunching. The list and those profiles still render, and so does a profile
   you reach by tapping a relative you opened earlier.
6. **A persistence layer.** Offline works because you wrote the records to disk,
   not because a view model happened to still be in memory. Use a real local
   store: one you define a schema for, that you can query, and that can return a
   single person by id without reading the whole set into memory. An embedded
   database or an object store built on one qualifies. A key-value preferences
   store, an in-memory dictionary, and an HTTP response cache on its own do not.
   Which store you pick is your decision, and so is how the rest of the app
   talks to it. Tell us what
   you chose, why, and what you would change if the list were 100,000 people
   instead of 16.
7. **Loading and failure states.** The user can tell the difference between
   "loading", "nothing here", and "that didn't work". A first launch with no
   network shows something better than a blank screen.

## The service

Base URL:

```
https://fs-records-sample.vercel.app
```

| Purpose | Request |
| --- | --- |
| List of people | `GET https://fs-records-sample.vercel.app/persons.json` |
| One person | `GET https://fs-records-sample.vercel.app/persons/{id}.json` |
| Portrait image | `GET https://fs-records-sample.vercel.app/{portraitUrl}` |

No authentication, no API key, no rate limit.

### Sample list response

`GET https://fs-records-sample.vercel.app/persons.json`

```json
{
  "updated": "2026-08-14",
  "count": 16,
  "persons": [
    {
      "id": "L4RX-9FT",
      "name": { "given": "Ezra", "surname": "Whitcomb" },
      "sex": "male",
      "living": false,
      "birth": {
        "date": "12 March 1868",
        "year": 1868,
        "place": "Nauvoo, Hancock, Illinois, United States"
      },
      "death": {
        "date": "3 November 1941",
        "year": 1941,
        "place": "Ogden, Weber, Utah, United States"
      },
      "portraitUrl": "portraits/L4RX-9FT.jpg"
    }
  ]
}
```

### Sample person response

`GET https://fs-records-sample.vercel.app/persons/L4RX-9FT.json`

Returns the same fields as a list entry, plus:

```json
{
  "occupation": "Wheelwright",
  "biography": "Apprenticed to his father in Nauvoo and moved west with his wife in 1897, where he kept a wheelwright's shop on Washington Avenue for thirty-one years.",
  "relatives": [
    {
      "id": "M2KD-7QP",
      "relationship": "father",
      "name": { "given": "Amos", "surname": "Whitcomb" },
      "birthYear": 1838,
      "deathYear": 1903
    }
  ],
  "sources": [
    {
      "title": "Illinois, County Marriages, 1810-1940",
      "citation": "Hancock County, vol. 7, p. 214"
    }
  ],
  "lastModified": "2026-08-14T09:21:00Z"
}
```

### Notes on the data

- `portraitUrl` is **relative to the base URL**. Resolve it before you request it.
- `death` is `null` for a living person. One person in the set is living.
- `birth.date` is a display string and is sometimes imprecise, for example
  `"about 1838"` or `"1810"`. `birth.year` is always an integer. Use `year` when
  you need to compute or sort, and `date` when you need to show something.
- `occupation` is `null` for some people.
- `relationship` is one of `father`, `mother`, `spouse`, `son`, or `daughter`.
- `sources` is often an empty array.

## What we left open on purpose

Some of the requirements above tell you *what* without telling you *how*. That's
deliberate. Where you find a gap, close it yourself and write down the call you
made and the reason for it. Don't email us for a ruling.

## Tests

Include tests where you think they earn their keep. We're interested in what you
chose to test and why, not in a coverage number.

## Dependencies

You can use third-party packages. List each one in your README with a sentence on
why it earns its place.

## Tools and AI assistants

Use the tools you use day to day, AI assistants included. We'd rather see how you
actually work. You will walk us through this code and answer questions about it,
so submit only what you can explain and defend.

## What to send us

A git repository, either as a link or as a zip that includes the `.git` folder.
We read the commit history.

Your README covers:

- How to run it
- What you decided and why, especially where the requirements were open
- Known gaps and anything you'd do differently
- What you'd do with another day
- Roughly how long you spent

It must build and run from a clean checkout using your platform's standard
toolchain and the steps in your README. Name the tool versions you used. If we
have to edit a file or hunt down an undocumented dependency to get it to build,
that counts against you.

## How we review it

- **Does it do what we asked**, particularly the offline behavior
- **Model and data layer**: how the wire format becomes your domain types, and
  what happens when the response isn't what you expected
- **Storage**: which persistence layer you chose, how records get in and out
  of it, and whether your reasoning holds up
- **Structure**: whether the pieces have clear jobs and could be changed
  independently
- **Concurrency**: how you handle work off the main thread and what happens when
  a screen goes away mid-request
- **Failure handling**: what the user sees when the network is down or the data
  is malformed
- **The README**: whether you can explain your own tradeoffs
