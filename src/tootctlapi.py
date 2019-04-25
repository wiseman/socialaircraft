import subprocess

from flask import Flask, request, jsonify

app = Flask(__name__)

@app.route('/api/v1/tootctl', methods=['POST'])
def tootctl():
    args = request.json
    print('Got request: %s' % (args,))
    full_args = ['bin/tootctl'] + args
    print('Full args: %s' % (full_args,))
    proc = subprocess.run(full_args, check=True, capture_output=True)
    return jsonify({'returnCode': proc.returncode,
                    'stdout': proc.stdout.decode('utf8'),
                    'stderr': proc.stderr.decode('utf8')})


if __name__ == '__main__':
    app.run(host='0.0.0.0', debug=False)
