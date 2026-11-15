import base64
import importlib.util
import json
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('preview_backend', Path(__file__).parents[1] / 'configure-preview-backend.py')
backend = importlib.util.module_from_spec(spec)
spec.loader.exec_module(backend)


def document(role='anon'):
    payload = base64.urlsafe_b64encode(json.dumps({'role': role}).encode()).decode().rstrip('=')
    return {'version': 1, 'service': 'nuvio', 'backend_url': backend.OFFICIAL_BACKEND,
            'publishable_key': f'e30.{payload}.signature', 'capabilities': {'tv_login': True}}


class PreviewBackendTest(unittest.TestCase):
    def test_configures_blank_build_for_qr_login(self):
        result = backend.complete_backend({}, document())
        self.assertEqual(result[backend.URL_KEY], backend.OFFICIAL_BACKEND)
        self.assertTrue(result[backend.PUBLIC_KEY])
        self.assertEqual(result['DEVICE_LOGIN_WEB_BASE_URL'], 'https://nuvio.tv/link')

    def test_preserves_complete_custom_configuration(self):
        existing = {backend.URL_KEY: 'https://my-server.example', backend.PUBLIC_KEY: 'custom-key'}
        self.assertEqual(backend.complete_backend(existing, {}), existing)

    def test_does_not_send_official_key_to_an_unconfigured_custom_server(self):
        with self.assertRaises(ValueError):
            backend.complete_backend({backend.URL_KEY: 'https://my-server.example'}, document())

    def test_rejects_privileged_key(self):
        with self.assertRaises(ValueError):
            backend.complete_backend({}, document('service_role'))

    def test_rejects_changed_backend(self):
        value = document()
        value['backend_url'] = 'https://unexpected.example'
        with self.assertRaises(ValueError):
            backend.complete_backend({}, value)

    def test_requires_advertised_tv_login(self):
        value = document()
        value['capabilities']['tv_login'] = False
        with self.assertRaises(ValueError):
            backend.complete_backend({}, value)


if __name__ == '__main__':
    unittest.main()
