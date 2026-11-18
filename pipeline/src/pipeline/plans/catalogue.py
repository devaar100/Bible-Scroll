"""The structural plans: computed from word counts, no AI. Copy is original, short, warm and plain."""
from __future__ import annotations

from dataclasses import dataclass

from .bible import NT_CODES, OT_CODES


@dataclass(frozen=True)
class StructuralPlan:
    id: str
    title: str
    short: str
    description: str
    category: str
    days: int
    #: Each track is read in parallel: day N of the plan = day N of every track.
    tracks: tuple[tuple[str, ...], ...]


def book_plan(code: str, name: str, days: int, category: str, short: str, about: str) -> StructuralPlan:
    return StructuralPlan(
        id=f"{name.lower().replace(' ', '-')}-{days}-days",
        title=f"{name} in {days} days",
        short=short,
        description=about,
        category=category,
        days=days,
        tracks=((code,),),
    )


GOSPELS = "gospels-and-acts"
LETTERS = "letters-and-revelation"
OT_STORIES = "old-testament-stories"
HABITS = "year-round-habits"

CATALOGUE: list[StructuralPlan] = [
    # ---- Year-round habits ----
    StructuralPlan(
        "bible-in-a-year", "The Bible in a year",
        "A little Old Testament and a little New Testament every day.",
        "Read the whole Bible in 365 days. Each day pairs a reading from the Old Testament with one from the "
        "New, so you're never far from the story of Jesus while you work through the history, poetry and "
        "prophets. Miss a day? Pick up where you left off; the plan waits for you.",
        HABITS, 365, (tuple(OT_CODES), tuple(NT_CODES)),
    ),
    StructuralPlan(
        "new-testament-90-days", "The New Testament in 90 days",
        "From the birth of Jesus to the new creation in three months.",
        "Read all 27 books of the New Testament, in order, in about three months: the four Gospels, the story "
        "of the early church, the letters and Revelation. A good way to see how the whole story fits together.",
        HABITS, 90, (tuple(NT_CODES),),
    ),
    StructuralPlan(
        "psalms-and-proverbs-month", "Psalms and Proverbs in a month",
        "Prayers and practical wisdom, side by side, for 31 days.",
        "Each day brings a few psalms and a chapter-sized portion of Proverbs. The psalms give words for "
        "praise, sorrow and trust; Proverbs gives plain advice for ordinary days. Read them together for a month.",
        HABITS, 31, (("PSA",), ("PRO",)),
    ),
    book_plan("PRO", "Proverbs", 31, HABITS, "A chapter of wisdom for every day of the month.",
              "Proverbs has 31 chapters, one for each day of a long month. Short sayings about work, words, "
              "money, friendship and character, meant to be read slowly and come back to."),
    book_plan("PSA", "Psalms", 60, HABITS, "Two months of the Bible's songs and prayers.",
              "Read all 150 psalms over 60 days. They cover every mood: joy, fear, anger, grief, thanks and "
              "hope. Many readers find a line here that becomes their own prayer."),

    # ---- Gospels and Acts ----
    StructuralPlan(
        "gospels-30-days", "The four Gospels in 30 days",
        "Matthew, Mark, Luke and John in a month.",
        "Read the four accounts of Jesus' life back to back. Each writer tells the story for different readers "
        "and notices different things; reading them together brings the whole picture into focus.",
        GOSPELS, 30, (("MAT", "MRK", "LUK", "JHN"),),
    ),
    book_plan("MAT", "Matthew", 28, GOSPELS, "Jesus the promised King, and the Sermon on the Mount.",
              "Matthew writes for readers who knew the Hebrew Scriptures, showing how Jesus fulfils their "
              "hopes. It holds the Sermon on the Mount and many of Jesus' best-known parables."),
    book_plan("MRK", "Mark", 16, GOSPELS, "The shortest Gospel: fast-moving and full of action.",
              "Mark is the shortest and fastest-paced Gospel, often moving \"immediately\" from one scene to "
              "the next. A good first book if you're new to the Bible."),
    book_plan("LUK", "Luke", 24, GOSPELS, "A careful account with a heart for outsiders.",
              "Luke set out to write an orderly account. His Gospel gives special attention to the poor, women, "
              "outsiders and prayer, and includes parables found nowhere else, like the prodigal son."),
    book_plan("JHN", "John", 21, GOSPELS, "Signs, conversations and the \"I am\" sayings of Jesus.",
              "John's Gospel is built around signs and long conversations, and was written \"so that you may "
              "believe\". Many people start reading the Bible here."),
    book_plan("ACT", "Acts", 28, GOSPELS, "How the early church grew from Jerusalem to Rome.",
              "Luke's second volume follows the first Christians, from the coming of the Spirit at Pentecost "
              "through Peter's and Paul's journeys, as the message spreads across the Roman world."),

    # ---- Letters and Revelation ----
    book_plan("ROM", "Romans", 16, LETTERS, "Paul's fullest letter on grace and faith.",
              "Romans is Paul's most complete explanation of the good news: what's wrong with the world, what "
              "God has done about it, and how that changes everyday life."),
    book_plan("GAL", "Galatians", 6, LETTERS, "Freedom in Christ, in six short readings.",
              "A passionate letter about grace, freedom and life in the Spirit, written to churches being told "
              "they needed more than Jesus."),
    book_plan("EPH", "Ephesians", 6, LETTERS, "Who you are in Christ, and how to live it.",
              "The first half of Ephesians describes what God has done; the second half turns that into "
              "everyday life in the church, at home and at work."),
    book_plan("PHP", "Philippians", 4, LETTERS, "A letter about joy, written from prison.",
              "Paul wrote this warm letter while in chains, and it is full of joy, thankfulness and "
              "encouragement not to worry. Four short days."),
    book_plan("COL", "Colossians", 4, LETTERS, "Christ above everything, in four days.",
              "A short letter centred on who Jesus is and what that means for how we think, speak and treat "
              "one another."),
    book_plan("HEB", "Hebrews", 13, LETTERS, "Jesus, the better hope, and the call to keep going.",
              "Hebrews shows how Jesus fulfils the Old Testament's priests and sacrifices, and encourages tired "
              "believers not to give up. It includes the great chapter on faith."),
    book_plan("JAS", "James", 5, LETTERS, "Faith that shows up in everyday life.",
              "James is practical and direct: about trials, the tongue, wealth, prayer and faith that acts. "
              "Five days, one short chapter each."),
    book_plan("1PE", "1 Peter", 5, LETTERS, "Hope for people going through hard times.",
              "Peter writes to scattered believers facing pressure and suffering, reminding them of their living "
              "hope and how to live well among neighbours who don't share it."),
    book_plan("1JN", "1 John", 5, LETTERS, "Light, love and the confidence of belonging to God.",
              "A gentle, repetitive letter about walking in the light, loving one another and knowing you "
              "belong to God."),
    book_plan("REV", "Revelation", 22, LETTERS, "A vision of Christ's victory and all things made new.",
              "Revelation uses vivid images to encourage churches under pressure: the Lamb has won, evil will "
              "end, and God will make all things new. Read it as hope, not a puzzle."),

    # ---- Old Testament stories ----
    book_plan("GEN", "Genesis", 50, OT_STORIES, "Creation, the first families, and God's promise to Abraham.",
              "The book of beginnings: creation, the fall, the flood, and the stories of Abraham, Isaac, Jacob "
              "and Joseph, through which God's promise to bless the world begins to unfold."),
    book_plan("EXO", "Exodus", 40, OT_STORIES, "Rescue from Egypt, the Ten Commandments, and God among his people.",
              "God hears his people's cry, rescues them from slavery, gives the law at Sinai, and comes to live "
              "among them. One of the Bible's great stories of freedom."),
    book_plan("JOS", "Joshua", 24, OT_STORIES, "Courage, promises kept, and a new land.",
              "After Moses, Joshua leads Israel into the promised land. A book about courage, faithfulness and "
              "the God who keeps his word."),
    book_plan("RUT", "Ruth", 4, OT_STORIES, "Loyalty, kindness and a quiet story of redemption.",
              "A short, beautiful story of a grieving widow, a loyal daughter-in-law, and the kindness that "
              "changes everything. Four days."),
    book_plan("1SA", "1 Samuel", 31, OT_STORIES, "Samuel, Saul and the rise of young David.",
              "Israel asks for a king. The story follows Samuel, Saul's rise and fall, and David from shepherd "
              "boy to giant-slayer to fugitive."),
    book_plan("EST", "Esther", 10, OT_STORIES, "Courage at just the right moment.",
              "A Jewish woman becomes queen of Persia and risks her life to save her people. God is never named, "
              "yet his care runs through every turn of the story."),
    book_plan("DAN", "Daniel", 12, OT_STORIES, "Faithfulness in a foreign land.",
              "Daniel and his friends stay faithful in Babylon: the fiery furnace, the lions' den, and visions "
              "of God's kingdom that outlasts every empire."),
    book_plan("JON", "Jonah", 4, OT_STORIES, "A reluctant prophet and a God of second chances.",
              "Jonah runs from God, is swallowed by a great fish, and is upset when God shows mercy. A short "
              "story with a surprisingly big heart."),
]

START_HERE = ["john-21-days", "mark-16-days", "psalms-and-proverbs-month", "james-5-days", "philippians-4-days"]

CATEGORIES = [
    {"id": "topics-and-seasons", "title": "Topics and seasons"},
    {"id": "gospels-and-acts", "title": "Gospels and Acts"},
    {"id": "letters-and-revelation", "title": "Letters and Revelation"},
    {"id": "old-testament-stories", "title": "Old Testament stories"},
    {"id": "year-round-habits", "title": "Year-round habits"},
]

#: Topical plans to draft with AI (7 days each).
TOPICS = ["anxiety", "grief", "hope", "forgiveness", "prayer", "identity", "peace", "gratitude", "courage", "loneliness"]
