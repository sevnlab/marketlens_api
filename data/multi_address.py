"""
도로명주소 DB 빌드 스크립트 (행안부 활용 가이드 기준)
- address_road/rnaddrkor_*.txt   → 도로명주소_한글 테이블
- address_jibun/jibun_rnaddrkor_*.txt → 관련지번 테이블
- 요약주소VIEW 생성 (가이드 기준 UNION ALL)
출력: D:/work_space/address/data/address.db
"""

import os, glob, sqlite3

ROAD_DIR  = r"D:\work_space\data\address_road"
JIBUN_DIR = r"D:\work_space\data\address_jibun"
DB_PATH   = r"D:\work_space\address\data\address.db"
ENCODING  = "cp949"
BATCH     = 10000

# ── rnaddrkor 컬럼 (24개) ──────────────────────────────
# [0]도로명주소관리번호 [1]법정동코드  [2]시도명  [3]시군구명
# [4]읍면동명  [5]리명  [6]산여부  [7]번지  [8]호
# [9]도로명코드  [10]도로명  [11]지하여부  [12]건물본번  [13]건물부번
# [14]행정동코드  [15]행정동명  [16]기초구역번호  [17]이동사유코드
# [18]고시일자  [19]공동주택여부  [20]건축물대장건물명  [21]건물명  [22~23]무시

# ── jibun_rnaddrkor 컬럼 (14개) ───────────────────────
# [0]도로명주소관리번호 [1]법정동코드  [2]시도명  [3]시군구명
# [4]읍면동명  [5]리명  [6]산여부  [7]지번본번  [8]지번부번
# [9]도로명코드(무시)  [10]대표여부  [11]관련지번순번  [12]이동사유코드


def create_tables(conn):
    conn.executescript("""
    DROP TABLE IF EXISTS 도로명주소_한글;
    DROP TABLE IF EXISTS 관련지번;
    DROP VIEW  IF EXISTS 요약주소VIEW;

    CREATE TABLE 도로명주소_한글 (
        도로명주소관리번호  TEXT,
        법정동코드          TEXT,
        시도명              TEXT,
        시군구명            TEXT,
        읍면동명            TEXT,
        리명                TEXT,
        산여부              TEXT,
        도로명코드          TEXT,
        도로명              TEXT,
        지하여부            TEXT,
        건물본번            INTEGER,
        건물부번            INTEGER,
        행정동코드          TEXT,
        행정동명            TEXT,
        기초구역번호        TEXT,
        이동사유코드        TEXT,
        고시일자            TEXT,
        공동주택여부        TEXT,
        건축물대장건물명    TEXT,
        건물명              TEXT
    );

    CREATE TABLE 관련지번 (
        도로명주소관리번호  TEXT,
        법정동코드          TEXT,
        시도명              TEXT,
        시군구명            TEXT,
        읍면동명            TEXT,
        리명                TEXT,
        산여부              TEXT,
        지번본번            TEXT,
        지번부번            TEXT,
        대표여부            TEXT,
        관련지번순번        TEXT,
        이동사유코드        TEXT
    );
    """)
    conn.commit()


def load_road(conn, path):
    cur = conn.cursor()
    batch, count = [], 0
    with open(path, "r", encoding=ENCODING) as f:
        for line in f:
            p = line.rstrip("\n").split("|")
            while len(p) < 22:
                p.append("")
            batch.append((
                p[0],  p[1],  p[2],  p[3],  p[4],  p[5],
                p[6],                                       # 산여부
                p[9],  p[10], p[11],                       # 도로명코드, 도로명, 지하여부
                int(p[12]) if p[12].isdigit() else 0,      # 건물본번
                int(p[13]) if p[13].isdigit() else 0,      # 건물부번
                p[14], p[15], p[16], p[17], p[18], p[19],  # 행정동코드~공동주택여부
                p[20], p[21],                               # 건축물대장건물명, 건물명
            ))
            if len(batch) >= BATCH:
                cur.executemany("INSERT INTO 도로명주소_한글 VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", batch)
                conn.commit(); count += len(batch); batch.clear()
                print(f"\r  {count:,}건...", end="")
    if batch:
        cur.executemany("INSERT INTO 도로명주소_한글 VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", batch)
        conn.commit(); count += len(batch)
    print(f"\r  완료: {count:,}건")
    return count


def load_jibun(conn, path):
    cur = conn.cursor()
    batch, count = [], 0
    with open(path, "r", encoding=ENCODING) as f:
        for line in f:
            p = line.rstrip("\n").split("|")
            while len(p) < 13:
                p.append("")
            batch.append((
                p[0], p[1], p[2], p[3], p[4], p[5],
                p[6], p[7], p[8],           # 산여부, 지번본번, 지번부번
                # p[9] = 도로명코드 → 스킵
                p[10], p[11], p[12],        # 대표여부, 관련지번순번, 이동사유코드
            ))
            if len(batch) >= BATCH:
                cur.executemany("INSERT INTO 관련지번 VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", batch)
                conn.commit(); count += len(batch); batch.clear()
                print(f"\r  {count:,}건...", end="")
    if batch:
        cur.executemany("INSERT INTO 관련지번 VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", batch)
        conn.commit(); count += len(batch)
    print(f"\r  완료: {count:,}건")
    return count


def create_indexes(conn):
    print("\n[4] 인덱스 생성...")
    for name, cols in [
        ("idx_rn_mgmt",     "도로명주소_한글(도로명주소관리번호)"),
        ("idx_rn_sido",     "도로명주소_한글(시도명)"),
        ("idx_rn_sigungu",  "도로명주소_한글(시군구명)"),
        ("idx_rn_dong",     "도로명주소_한글(읍면동명)"),
        ("idx_rn_road",     "도로명주소_한글(도로명)"),
        ("idx_rn_bldg",     "도로명주소_한글(건물명)"),
        ("idx_rn_bldgno",   "도로명주소_한글(건물본번)"),
        ("idx_rn_zip",      "도로명주소_한글(기초구역번호)"),
        ("idx_jibun_mgmt",  "관련지번(도로명주소관리번호)"),
        ("idx_jibun_dong",  "관련지번(읍면동명, 지번본번, 지번부번)"),
    ]:
        print(f"  {name}...", end="", flush=True)
        conn.execute(f"CREATE INDEX IF NOT EXISTS {name} ON {cols}")
        conn.commit()
        print(" OK")


def create_view(conn):
    print("\n[5] 요약주소VIEW 생성...")
    conn.executescript("""
    DROP VIEW IF EXISTS 요약주소VIEW;
    CREATE VIEW 요약주소VIEW AS
    SELECT
        A.기초구역번호, A.도로명주소관리번호, A.법정동코드,
        A.시도명, A.시군구명, A.읍면동명, A.리명, A.산여부,
        A.도로명코드, A.도로명,
        NULL AS 지번본번, NULL AS 지번부번,
        A.지하여부, A.건물본번, A.건물부번,
        A.공동주택여부, A.건축물대장건물명, A.건물명
    FROM 도로명주소_한글 A
    UNION ALL
    SELECT
        A.기초구역번호, A.도로명주소관리번호, A.법정동코드,
        A.시도명, A.시군구명, A.읍면동명, A.리명, A.산여부,
        A.도로명코드, A.도로명,
        X.지번본번, X.지번부번,
        A.지하여부, A.건물본번, A.건물부번,
        A.공동주택여부, A.건축물대장건물명, A.건물명
    FROM 도로명주소_한글 A
    JOIN 관련지번 X ON A.도로명주소관리번호 = X.도로명주소관리번호;
    """)
    conn.commit()
    print("  완료")


def main():
    if os.path.exists(DB_PATH):
        os.remove(DB_PATH)
        print(f"기존 DB 삭제: {DB_PATH}")

    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA journal_mode = WAL")
    conn.execute("PRAGMA synchronous  = NORMAL")
    conn.execute("PRAGMA cache_size   = -65536")  # 64MB cache

    print("[1] 테이블 생성...")
    create_tables(conn)

    road_files  = sorted(glob.glob(os.path.join(ROAD_DIR,  "rnaddrkor_*.txt")))
    jibun_files = sorted(glob.glob(os.path.join(JIBUN_DIR, "jibun_rnaddrkor_*.txt")))
    print(f"    도로명 {len(road_files)}개 / 지번 {len(jibun_files)}개 파일\n")

    total_road = 0
    print("[2] 도로명주소 적재...")
    for f in road_files:
        print(f"  {os.path.basename(f)}")
        total_road += load_road(conn, f)

    total_jibun = 0
    print("\n[3] 관련지번 적재...")
    for f in jibun_files:
        print(f"  {os.path.basename(f)}")
        total_jibun += load_jibun(conn, f)

    create_indexes(conn)
    create_view(conn)
    conn.close()

    print(f"\n완료!")
    print(f"  도로명주소: {total_road:,}건")
    print(f"  관련지번:   {total_jibun:,}건")
    print(f"  저장 위치:  {DB_PATH}")


if __name__ == "__main__":
    main()
