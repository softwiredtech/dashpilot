# ADAS simulator

## Installation

1. Create a python3 virtual environment

`python3 -m venv venv`

2. Activate the environment

`source ./venv/bin/activate`

3. Install requirements

`pip3 install -r requirements.txt`

## How to use

1. Run the server

For dynamic scenario generator:  

`python3 server.py`

For route replay (recorded with Cabana):  

`python3 server.py --route '/path/to/your/route_20260211_142908_598.jsonl'`

2. Run `dash-apps/web`

