#!/usr/bin/env python3
"""Validate a portable config; optionally merge it over a previous config."""
import json, math, re, sys
from pathlib import Path
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

BOOLEANS = {'show_weather', 'show_aqi', 'show_gold', 'show_fuel', 'show_lunar', 'show_holidays'}
RANGES = {'weather_latitude': (-90,90), 'weather_longitude': (-180,180),
          'home_idle_clock_minutes': (0,1440), 'sky_intensity': (0,100),
          'gold_refresh_minutes': (5,240), 'fuel_refresh_minutes': (15,720)}

def load(path):
    raw=Path(path).read_bytes()
    if len(raw)>65536: raise ValueError('Config exceeds 64 KiB')
    value=json.loads(raw.decode('utf-8-sig'), parse_constant=lambda s: (_ for _ in ()).throw(ValueError('Non-finite JSON number')))
    if not isinstance(value,dict): raise ValueError('Config must be a JSON object')
    return value

def validate_personal(o):
    supported=BOOLEANS | RANGES.keys() | {'weather_place','display_timezone','background_color','gold_brand','home_layout'}
    for key,value in o.items():
        if key not in supported: raise ValueError('Unsupported personal key: '+key)
        if key in BOOLEANS and type(value) is not bool: raise ValueError(key+': expected boolean')
        if key in RANGES:
            lo,hi=RANGES[key]
            if type(value) not in (int,float) or not math.isfinite(value) or not lo<=value<=hi: raise ValueError(key+': outside range')
            if key not in ('weather_latitude','weather_longitude') and value!=int(value): raise ValueError(key+': expected integer')
        if key=='weather_place' and (not isinstance(value,str) or not value.strip() or len(value)>60): raise ValueError('Place must have 1–60 characters')
        if key=='display_timezone':
            if not isinstance(value,str): raise ValueError('Expected timezone name')
            if value!='system': ZoneInfo(value)
        if key=='background_color' and (not isinstance(value,str) or not re.fullmatch(r'#[0-9a-fA-F]{6}',value)): raise ValueError('Color must be #RRGGBB')
        if key=='home_layout' and value not in ('clock_left','clock_right','weather_right'): raise ValueError('Unknown Home layout')
        if key=='gold_brand' and value not in ('doji','pnj','phuquy','btmh'): raise ValueError('Unknown gold dealer')
    if ('weather_latitude' in o)!=('weather_longitude' in o): raise ValueError('Provide both coordinates')

if __name__=='__main__':
    try:
        kind,path,*base=sys.argv[1:]
        o=load(path)
        if base:
            original=load(base[0]); original.update(o); o=original
        if kind=='personal': validate_personal(o)
        print(json.dumps(o,ensure_ascii=False,allow_nan=False))
    except (ValueError,OSError,ZoneInfoNotFoundError) as e:
        sys.exit(str(e))
