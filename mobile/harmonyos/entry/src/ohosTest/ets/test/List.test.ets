import abilityTest from './Ability.test';
import nativeIntegrationTest from './NativeIntegration.test';

export default function testsuite(): void {
  abilityTest();
  nativeIntegrationTest();
}
