watch -n 1 "\
    true \
    && echo -n "lbalr:" && docker exec -it lb cat /proc/net/tcp /proc/net/tcp6 | cut -d' ' -f5 | grep 270F | wc -l \
    && echo -n "api01:" && docker exec -it api01 cat /proc/net/tcp /proc/net/tcp6 | cut -d' ' -f5 | grep 2706 | wc -l \
    && echo -n "api02:" && docker exec -it api02 cat /proc/net/tcp /proc/net/tcp6 | cut -d' ' -f5 | grep 2706 | wc -l \
    && echo -n "datab:" && docker exec -it db cat /proc/net/tcp /proc/net/tcp6  | wc -l \
    && PGPASSWORD=123 psql -h localhost -U rinha -d rinha -p 5400 -c \
        'SELECT COUNT(*) FROM pg_stat_activity;' \
    "
