# Apply the local UxPlay patches (patches/UxPlay/*.patch) onto a copy of the submodule's lib/ directory at
# configure time, so the submodule itself stays pristine (clean `git status`, trivial upstream bumps).
#
# Output: UXPLAY_LIB — absolute path to the patched lib/ directory (inside CMAKE_BINARY_DIR).
# Re-applied automatically whenever a patch, the pinned submodule commit, or any lib/ source changes.
#
# Check patches outside a build with: tools/third-party.sh verify-patches

find_package(Git QUIET)
if(NOT Git_FOUND)
    message(FATAL_ERROR "git is required to apply the UxPlay patches")
endif()

set(_uxplay_src   "${CMAKE_CURRENT_SOURCE_DIR}/third_party/UxPlay")
set(_uxplay_patch "${CMAKE_CURRENT_SOURCE_DIR}/patches/UxPlay")
set(_uxplay_out   "${CMAKE_BINARY_DIR}/uxplay-patched")

if(NOT EXISTS "${_uxplay_src}/lib/raop.c")
    # plain `git clone` leaves submodules empty — fetch them instead of failing when this is a git checkout
    get_filename_component(_repo_root "${CMAKE_CURRENT_SOURCE_DIR}/../../../.." ABSOLUTE)
    if(EXISTS "${_repo_root}/.gitmodules" AND EXISTS "${_repo_root}/.git")
        message(STATUS "third_party/ is empty — running git submodule update --init --recursive (first time only)")
        execute_process(
            COMMAND "${GIT_EXECUTABLE}" submodule update --init --recursive
            WORKING_DIRECTORY "${_repo_root}"
            RESULT_VARIABLE _rc
        )
        if(NOT _rc EQUAL 0)
            message(FATAL_ERROR "git submodule update failed — run it manually in ${_repo_root}")
        endif()
    else()
        message(FATAL_ERROR
            "third_party/UxPlay is empty and this is not a git checkout (downloaded ZIP?). "
            "Either clone with `git clone --recurse-submodules` or download the "
            "`airplay-core-<version>-full-source.tar.gz` asset from the GitHub release, which bundles all submodules.")
    endif()
endif()

file(GLOB _uxplay_patches CONFIGURE_DEPENDS "${_uxplay_patch}/*.patch")
list(SORT _uxplay_patches)
file(GLOB_RECURSE _uxplay_lib_sources CONFIGURE_DEPENDS "${_uxplay_src}/lib/*")

# fingerprint of inputs → skip re-applying when nothing changed
execute_process(
    COMMAND "${GIT_EXECUTABLE}" -C "${_uxplay_src}" rev-parse HEAD
    OUTPUT_VARIABLE _uxplay_head OUTPUT_STRIP_TRAILING_WHITESPACE ERROR_QUIET
)
set(_uxplay_stamp_content "${_uxplay_head}\n")
foreach(_p IN LISTS _uxplay_patches)
    file(SHA256 "${_p}" _h)
    string(APPEND _uxplay_stamp_content "${_h} ${_p}\n")
endforeach()
foreach(_f IN LISTS _uxplay_lib_sources)
    file(TIMESTAMP "${_f}" _ts "%Y%m%d%H%M%S" UTC)
    string(APPEND _uxplay_stamp_content "${_ts} ${_f}\n")
endforeach()
string(SHA256 _uxplay_stamp "${_uxplay_stamp_content}")

set(_uxplay_stamp_file "${_uxplay_out}/.stamp")
set(_uxplay_prev "")
if(EXISTS "${_uxplay_stamp_file}")
    file(READ "${_uxplay_stamp_file}" _uxplay_prev)
endif()

if(NOT _uxplay_prev STREQUAL _uxplay_stamp OR NOT EXISTS "${_uxplay_out}/lib/raop.c")
    list(LENGTH _uxplay_patches _n)
    message(STATUS "UxPlay: copying lib/ from ${_uxplay_head} and applying ${_n} patches")
    file(REMOVE_RECURSE "${_uxplay_out}")
    file(MAKE_DIRECTORY "${_uxplay_out}")
    file(COPY "${_uxplay_src}/lib" DESTINATION "${_uxplay_out}")

    foreach(_p IN LISTS _uxplay_patches)
        get_filename_component(_name "${_p}" NAME)
        # patches are rooted at the UxPlay checkout (a/lib/...); the copy only holds lib/, hence --directory.
        # GIT_CEILING_DIRECTORIES stops git from discovering the enclosing airplay-core repo (the build dir
        # lives inside it) and silently resolving paths against that instead of the copy.
        execute_process(
            COMMAND "${CMAKE_COMMAND}" -E env "GIT_CEILING_DIRECTORIES=${CMAKE_BINARY_DIR}" "GIT_DIR=${_uxplay_out}/.nogit"
                    "${GIT_EXECUTABLE}" apply --unidiff-zero --directory=lib -p2 "${_p}"
            WORKING_DIRECTORY "${_uxplay_out}"
            RESULT_VARIABLE _rc
            ERROR_VARIABLE _err
        )
        if(NOT _rc EQUAL 0)
            file(REMOVE_RECURSE "${_uxplay_out}")
            message(FATAL_ERROR
                "UxPlay patch failed: ${_name}\n${_err}\n"
                "The pinned submodule commit (${_uxplay_head}) and patches/UxPlay are out of sync. "
                "Rebase the patch or pin UxPlay back; see tools/third-party.sh")
        endif()
        message(STATUS "UxPlay: applied ${_name}")
    endforeach()
    file(WRITE "${_uxplay_stamp_file}" "${_uxplay_stamp}")
endif()

set(UXPLAY_LIB "${_uxplay_out}/lib")
