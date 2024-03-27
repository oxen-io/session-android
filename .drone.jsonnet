local docker_base = 'registry.oxen.rocks/lokinet-ci-';

// Log a bunch of version information to make it easier for debugging
local version_info = {
  name: 'Version Information',
  image: docker_base + 'android',
  commands: [
    'cmake --version',
    'apt --installed list'
  ]
};


// Intentionally doing a depth of 2 as libSession-util has it's own submodules (and libLokinet likely will as well)
local clone_submodules = {
  name: 'Clone Submodules',
  image: 'drone/git',
  commands: ['git fetch --tags', 'git submodule update --init --recursive --depth=2 --jobs=4']
};

// cmake options for static deps mirror
local ci_dep_mirror(want_mirror) = (if want_mirror then ' -DLOCAL_MIRROR=https://oxen.rocks/deps ' else '');

[
  // Unit tests (PRs only)
  {
    kind: 'pipeline',
    type: 'docker',
    name: 'Unit Tests',
    platform: { arch: 'amd64' },
    trigger: { event: { exclude: [ 'push' ] } },
    steps: [
      version_info,
      clone_submodules,
      {
        name: 'Run Unit Tests',
        image: docker_base + 'android',
        pull: 'always',
        environment: { ANDROID_HOME: '/usr/lib/android-sdk' },
        commands: [
          'apt-get install -y ninja-build',
          './gradlew testPlayDebugUnitTestCoverageReport'
        ],
      }
    ],
  },
  // Validate build artifact was created by the direct branch push (PRs only)
  {
    kind: 'pipeline',
    type: 'docker',
    name: 'Check Build Artifact Existence',
    platform: { arch: 'amd64' },
    trigger: { event: { exclude: [ 'push' ] } },
    steps: [
      {
        name: 'Poll for build artifact existence',
        image: docker_base + 'android',
        pull: 'always',
        commands: [
          './scripts/drone-upload-exists.sh'
        ]
      }
    ]
  },
  // Debug APK build (non-PRs only)
  {
    kind: 'pipeline',
    type: 'docker',
    name: 'Debug APK Build',
    platform: { arch: 'amd64' },
    trigger: { event: { exclude: [ 'pull_request' ] } },
    steps: [
      version_info,
      clone_submodules,
      {
        name: 'Build and upload',
        image: docker_base + 'android',
        pull: 'always',
        environment: { SSH_KEY: { from_secret: 'SSH_KEY' }, ANDROID_HOME: '/usr/lib/android-sdk' },
        commands: [
          'apt-get install -y ninja-build',
          './gradlew assemblePlayDebug',
          './scripts/drone-static-upload.sh'
        ],
      }
    ],
  }
]