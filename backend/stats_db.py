
import sqlite3

from datetime import datetime

from pathlib import Path



DB_PATH = Path(__file__).parent / "stats.db"



def get_conn():

    return sqlite3.connect(DB_PATH)



def init_db():

    with get_conn() as conn:

        conn.execute("""

        CREATE TABLE IF NOT EXISTS analysis_stats (

            id INTEGER PRIMARY KEY AUTOINCREMENT,

            created_at TEXT NOT NULL,

            source TEXT NOT NULL,

            risk TEXT NOT NULL,

            is_scam INTEGER NOT NULL,

            categories TEXT,

            actions TEXT,

            model_used INTEGER,

            total_time_ms INTEGER

        )

        """)

        conn.commit()



def insert_stat(

    source: str,

    risk: str,

    is_scam: bool,

    categories: list,

    actions: list,

    model_used: bool,

    total_time_ms: int

):

    with get_conn() as conn:

        conn.execute("""

        INSERT INTO analysis_stats (

            created_at, source, risk, is_scam,

            categories, actions, model_used, total_time_ms

        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)

        """, (

            datetime.utcnow().isoformat(),

            source,

            risk,

            1 if is_scam else 0,

            ",".join(categories),

            ",".join(actions),

            1 if model_used else 0,

            total_time_ms

        ))

        conn.commit()

