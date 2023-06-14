#!/usr/bin/env python

import argparse
import http.server
import os
import hashlib
import glob
import re

class HTTPRequestHandler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        atakcode = 0
        for header in self.headers:
            if header.lower() == "atak-code":
                atakcode = self.headers[header]
        if not atakcode:
            self.send_response(404)
            self.end_headers()
            return
        else:
#            filename = glob.glob("/upload/*.%s.zip"%atakcode)
            for name in glob.glob("/upload/*.zip"):
                if "."+atakcode in name:
                    filename = name
            if not filename:
                self.send_response(404)
                self.end_headers()
                return
            with open(filename, 'rb') as f:
                data = f.read()
                self.send_response(200)
                self.send_header("Content-length", len(data))
                self.send_header("Content-type", "application/zip")
                self.send_header("File-name", "%szip"%(filename.split(os.sep)[-1])[0:len(filename.split(os.sep)[-1])-(128+4)])
                self.end_headers()
                self.wfile.write(data)

    def do_PUT(self):
        path = self.translate_path(self.path)
        if path.endswith('/') or "../" in path:
            self.send_response(400)
            self.end_headers()
            return
        else:
            try:
                length = int(self.headers['Content-Length'])
            except:
                self.send_response(400)
                self.end_headers()
                return
            data = self.rfile.read(length)
            atakcode = hashlib.sha512(data).hexdigest()
            filename = os.path.basename(path).split(".")[0]
            with open("/upload/%s.%s.zip"%(filename,atakcode), 'wb') as f:
                f.write(data)
            self.send_response(201)
            self.end_headers()
            self.wfile.write(atakcode.encode())

    def do_POST(self):
       self.send_reponse(404)
       self.end_headers() 

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--bind', '-b', default='', metavar='ADDRESS',
                        help='Specify alternate bind address '
                             '[default: all interfaces]')
    parser.add_argument('port', action='store',
                        default=8000, type=int,
                        nargs='?',
                        help='Specify alternate port [default: 8000]')
    args = parser.parse_args()

    http.server.test(HandlerClass=HTTPRequestHandler, port=args.port, bind=args.bind)
