# cloudDP
 ATAK Plugin for secure datapackage sharing

# systemd service /etc/systemd/system/atakcode.service
 An example systemd service configuration

    [Unit]
    Description=atakcode Service
    After=network.target
    StartLimitIntervalSec=0
    
    [Service]
    Type=simple
    Restart=always
    RestartSec=1
    User=nick
    ExecStartPre=
    ExecStart=python3 /home/user/server.py --bind 127.0.0.1 8000
    ExecStartPost
    ExecStop=
    ExecReload=
    
    [Install]
    WantedBy=multi-user.target

# ngnix config /etc/nginx/sites-available/hostname


    server {
    
            root /var/www/hostname/html;
            index index.html
    
            server_name hostname;
    
    
            location / {
                    client_max_body_size 5M;
    
                    include proxy_params;
                    proxy_pass http://127.0.0.1:8000;
            }
    
        listen [::]:443 ssl ipv6only=on; # managed by Certbot
        listen 443 ssl; # managed by Certbot
        ssl_certificate /etc/letsencrypt/live/hostname/fullchain.pem; # managed by Certbot
        ssl_certificate_key /etc/letsencrypt/live/hostname/privkey.pem; # managed by Certbot
        include /etc/letsencrypt/options-ssl-nginx.conf; # managed by Certbot
        ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem; # managed by Certbot
    
    }
    server {
        if ($host = hostname) {
            return 301 https://$host$request_uri;
        } # managed by Certbot
    
    
            listen 80;
            listen [::]:80;
    
            server_name hostname;
        return 404; # managed by Certbot
    }

Note: Replace hostname with your actual fqdn
