import hashlib,importlib.util,unittest
from pathlib import Path

def module(name):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).parents[1]/(name+'.py'))
    m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
u=module('update');c=module('config')
class Tools(unittest.TestCase):
    def test_checksum_rejects_tampering_and_duplicate(self):
        valid=hashlib.sha256(b'apk').hexdigest()+'  app.apk'
        u.verify(b'apk',valid,'app.apk')
        for data,sums in [(b'bad',valid),(b'apk',valid+'\n'+valid)]:
            with self.assertRaises(ValueError):u.verify(data,sums,'app.apk')
    def test_select_rejects_external_or_incomplete_assets(self):
        release={'tag_name':'tv-home-viet-v1.0.2','assets':[]}
        with self.assertRaises(ValueError):u.select([release])
        prefix=f'https://github.com/{u.REPO}/releases/download/tv-home-viet-v1.0.2/'
        names=['TV-Home-Viet-1.0.2.apk','TV-Home-Viet-1.0.2-SHA256SUMS.txt']
        release['assets']=[{'name':n,'browser_download_url':prefix+n} for n in names]
        self.assertEqual(u.select([release])[1],names[0])
        release['assets'][0]['browser_download_url']='https://other.example/app.apk'
        with self.assertRaises(ValueError):u.select([release])
    def test_newest_semver_beats_api_order(self):
        releases=[]
        for version in ['1.0.1','1.10.0','1.2.0']:
            tag='tv-home-viet-v'+version
            names=[f'TV-Home-Viet-{version}.apk',f'TV-Home-Viet-{version}-SHA256SUMS.txt']
            releases.append({'tag_name':tag,'assets':[{'name':n,'browser_download_url':f'https://github.com/{u.REPO}/releases/download/{tag}/{n}'} for n in names]})
        self.assertEqual(u.select(releases)[0],'tv-home-viet-v1.10.0')
        self.assertEqual(u.select(releases,'tv-home-viet-v1.0.1')[0],'tv-home-viet-v1.0.1')
    def test_personal_boundary_validation(self):
        c.validate_personal({'weather_latitude':-90,'weather_longitude':180,'display_timezone':'Asia/Tokyo','show_weather':False})
        for o in [{'weather_latitude':0},{'sky_intensity':True},{'sky_intensity':1.5},{'gold_brand':'wrong'},{'show_aqi':'true'},{'weather_latitude':float('nan'),'weather_longitude':0}]:
            with self.assertRaises(ValueError):c.validate_personal(o)
if __name__=='__main__':unittest.main()
