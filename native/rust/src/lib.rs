#[macro_use]
extern crate log;

mod common;

mod hook;
mod util;
mod mapped_lib;
mod config;
mod sig;

mod modules;
mod security;
mod secstrings;

use android_logger::Config;
use log::LevelFilter;
use modules::{valdi_hook, custom_font_hook, duplex_hook, fstat_hook, linker_hook, sqlite_hook, unary_call_hook};

use jni::{JNIEnv, JavaVM, NativeMethod};
use jni::objects::{JObject, JString, JClass, JValue};
use jni::sys::{jint, jstring, JNI_VERSION_1_6, jboolean, JNI_FALSE, JNI_TRUE};
use std::ffi::c_void;
use std::sync::atomic::{AtomicBool, Ordering};
use once_cell::sync::Lazy;
use std::collections::HashMap;
use std::sync::Mutex;

static TEST_MODE: AtomicBool = AtomicBool::new(false);
static IN_LOGIN_SIGNUP: AtomicBool = AtomicBool::new(false);
static CHECKSUMS: Lazy<Mutex<HashMap<String, u32>>> = Lazy::new(|| Mutex::new(HashMap::new()));

struct BlockerDecision {
    blocked: bool,
    reason: &'static str,
    keyword: Option<String>,
    keyword_context: Option<&'static str>,
    match_type: Option<&'static str>,
    match_value: Option<String>,
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn JNI_OnLoad(_vm: JavaVM, _: *mut c_void) -> jint {
    android_logger::init_once(
        Config::default()
        .with_max_level(LevelFilter::Debug)
        .with_tag("PurrfectSnapNative")
    );
    
    info!("JNI_OnLoad called");

    security::start_anti_debug_thread();

    std::panic::set_hook(Box::new(|panic_info| {
        error!("{:?}", panic_info);
    }));

    common::set_java_vm(_vm.get_java_vm_pointer());

    let mut env = _vm.get_env().expect("Failed to get JNIEnv");

    let native_lib_class = env.find_class("cock/crest/purrfectsnap/lite/nativelib/NativeLib").expect("NativeLib class not found");

    env.register_native_methods(
        native_lib_class,
        &[
            NativeMethod {
                name: "preInit".into(),
                sig: "()V".into(),
                fn_ptr: pre_init as *mut c_void,
            },
            NativeMethod {
                name: "init".into(),
                sig: "(Ljava/lang/String;)Ljava/lang/String;".into(),
                fn_ptr: init as *mut c_void,
            },
            NativeMethod {
                name: "loadConfig".into(),
                sig: "(Lcock/crest/purrfectsnap/lite/nativelib/NativeConfig;)V".into(),
                fn_ptr: config::load_config as *mut c_void,
            },
            NativeMethod {
                name: "addLinkerSharedLibrary".into(),
                sig: "(Ljava/lang/String;[B)V".into(),
                fn_ptr: linker_hook::add_linker_shared_library as *mut c_void,
            },
            NativeMethod {
                name: "lockDatabase".into(),
                sig: "(Ljava/lang/String;Ljava/lang/Runnable;)V".into(),
                fn_ptr: sqlite_hook::lock_database as *mut c_void,
            },
            NativeMethod {
                name: "setValdiLoader".into(),
                sig: "(Ljava/lang/String;)V".into(),
                fn_ptr: valdi_hook::set_valdi_loader as *mut c_void,
            },
            NativeMethod {
                name: "evaluateEndpointNative".into(),
                sig: "(Ljava/lang/String;Ljava/lang/String;ZLcock/crest/purrfectsnap/lite/nativelib/NativeDecision;)V".into(),
                fn_ptr: evaluateEndpoint as *mut c_void,
            },
            NativeMethod {
                name: "evaluateNetworkRequestNative".into(),
                sig: "(Ljava/lang/String;Lcock/crest/purrfectsnap/lite/nativelib/NativeDecision;)V".into(),
                fn_ptr: evaluateNetworkRequest as *mut c_void,
            },
            NativeMethod {
                name: "shouldBlockDuplexClient".into(),
                sig: "(Ljava/lang/String;)Z".into(),
                fn_ptr: shouldBlockDuplexClient as *mut c_void,
            },
            NativeMethod {
                name: "evaluateAuthContextNative".into(),
                sig: "(Ljava/lang/String;ZLcock/crest/purrfectsnap/lite/nativelib/NativeDecision;)V".into(),
                fn_ptr: evaluateAuthContext as *mut c_void,
            },
            NativeMethod {
                name: "evaluateApiInvocationNative".into(),
                sig: "(Ljava/lang/String;Ljava/lang/String;Lcock/crest/purrfectsnap/lite/nativelib/NativeDecision;)V".into(),
                fn_ptr: evaluateApiInvocation as *mut c_void,
            },
            NativeMethod {
                name: "runEndpointSelfTest".into(),
                sig: "(Z)Z".into(),
                fn_ptr: runEndpointSelfTest as *mut c_void,
            },
            NativeMethod {
                name: "setChecksums".into(),
                sig: "(Ljava/lang/String;)V".into(),
                fn_ptr: setChecksums as *mut c_void,
            },
            NativeMethod {
                name: "setTestMode".into(),
                sig: "(Z)V".into(),
                fn_ptr: setTestMode as *mut c_void,
            },
            NativeMethod {
                name: "setInLoginSignup".into(),
                sig: "(Z)V".into(),
                fn_ptr: setInLoginSignup as *mut c_void,
            },
        ]
    ).expect("Failed to register native methods");

    JNI_VERSION_1_6
}

#[allow(non_snake_case)]
fn setChecksums(mut env: JNIEnv, _class: JClass, checksums_json: JString) {
    let checksums_str: String = env.get_string(&checksums_json).unwrap().into();
    let checksums: HashMap<String, u32> = serde_json::from_str(&checksums_str).unwrap();
    *CHECKSUMS.lock().unwrap() = checksums;
}

fn pre_init(_env: JNIEnv, _class: JObject) {
    debug!("Pre init");
    for (name, init) in [
        ("linker_hook", linker_hook::init as fn()),
        ("custom_font_hook", custom_font_hook::init as fn()),
        ("fstat_hook", fstat_hook::init as fn()),
    ] {
        if let Err(error) = std::panic::catch_unwind(init) {
            error!("{} init failed: {:?}", name, error);
        }
    }
}

fn init(mut env: JNIEnv, _class: JObject, signature_cache: JString) -> jstring {
    debug!("Initializing native lib");

    let start_time = std::time::Instant::now();

    // load signature cache
    
    if !signature_cache.is_null() {
        let sig_cache_str = util::get_jni_string(&mut env, signature_cache).expect("Failed to convert mappings to string");
        
        if let Ok(signature_cache) = serde_json::from_str(sig_cache_str.as_str()) {
            sig::add_signatures(signature_cache);
        } else {
            error!("Failed to load signature cache");
        }
    }

    common::set_native_lib_instance(env.new_global_ref(_class).ok().expect("Failed to create global ref"));

    let _ = common::CLIENT_MODULE;

    // initialize modules asynchronously

    let mut threads: Vec<std::thread::JoinHandle<()>> = Vec::new();

    macro_rules! async_init {
        ($(($name:expr, $f:expr)),* $(,)?) => {
            $(
                threads.push(std::thread::spawn(move || {
                    if let Err(error) = std::panic::catch_unwind(|| { $f; }) {
                        error!("{} init failed: {:?}", $name, error);
                    }
                }));
            )*
        };
    }

    async_init!(
        ("duplex_hook", duplex_hook::init()),
        ("unary_call_hook", unary_call_hook::init()),
        ("valdi_hook", valdi_hook::init()),
        ("sqlite_hook", sqlite_hook::init())
    );
    
    threads.into_iter().for_each(|t| {
        if let Err(error) = t.join() {
            error!("native init worker panicked: {:?}", error);
        }
    });

    info!("native init took {:?}", start_time.elapsed());

    // send back the signature cache
    if let Ok(signature_cache) = serde_json::to_string(&sig::get_signatures()) {
        env.new_string(signature_cache).ok().expect("Failed to create new string").into_raw()
    } else {
        std::ptr::null_mut()
    }
}


fn find_keyword(paths: &[&str], keywords: &[String]) -> Option<String> {
    for path in paths {
        let lower_path = path.to_lowercase();
        for keyword in keywords {
            if lower_path.contains(keyword) {
                return Some(keyword.clone());
            }
        }
    }
    None
}

fn evaluate_endpoint_logic(
    config: &config::BlockerConfig,
    uri: &str,
    arg0: &str,
    has_attestation: bool,
) -> BlockerDecision {
    let targets = [uri, arg0];
    if let Some(matched) = find_keyword(&targets, &config.allowed_eps_active) {
        return BlockerDecision {
            blocked: false,
            reason: "allowed_whitelist",
            keyword: None,
            keyword_context: None,
            match_type: Some("allowed_whitelist"),
            match_value: Some(matched),
        };
    }
    let detection_keyword = find_keyword(&targets, &config.detection_keywords);
    if let Some(matched) = find_keyword(&targets, &config.risk_block_list) {
        return BlockerDecision {
            blocked: true,
            reason: "risk_blocklist",
            keyword: detection_keyword,
            keyword_context: None,
            match_type: Some("risk_blocklist"),
            match_value: Some(matched),
        };
    }

    if let Some(keyword) = detection_keyword {
        let reason = if has_attestation {
            "attestation+keyword"
        } else {
            "detection_keyword"
        };
        return BlockerDecision {
            blocked: true,
            reason,
            keyword: Some(keyword.clone()),
            keyword_context: None,
            match_type: Some("detection_keyword"),
            match_value: Some(keyword),
        };
    }

    BlockerDecision {
        blocked: false,
        reason: "allowed",
        keyword: None,
        keyword_context: None,
        match_type: None,
        match_value: None,
    }
}

fn evaluate_network_request_logic(
    config: &config::BlockerConfig,
    url: &str,
) -> BlockerDecision {
    if let Some(keyword) = find_keyword(&[url], &config.detection_keywords) {
        return BlockerDecision {
            blocked: true,
            reason: "detection_keyword",
            keyword: Some(keyword.clone()),
            keyword_context: None,
            match_type: Some("detection_keyword"),
            match_value: Some(keyword),
        };
    }

    BlockerDecision {
        blocked: false,
        reason: "allowed",
        keyword: None,
        keyword_context: None,
        match_type: None,
        match_value: None,
    }
}

fn evaluate_auth_context_logic(
    config: &config::BlockerConfig,
    request_path: &str,
    attestation_required: bool,
) -> BlockerDecision {
    let targets = [request_path];
    if let Some(matched) = find_keyword(&targets, &config.allowed_eps_active) {
        return BlockerDecision {
            blocked: false,
            reason: "allowed_whitelist",
            keyword: None,
            keyword_context: None,
            match_type: Some("allowed_whitelist"),
            match_value: Some(matched),
        };
    }
    let detection_keyword = find_keyword(&targets, &config.detection_keywords);
    if let Some(matched) = find_keyword(&targets, &config.risk_block_list) {
        return BlockerDecision {
            blocked: true,
            reason: "risk_blocklist",
            keyword: detection_keyword,
            keyword_context: None,
            match_type: Some("risk_blocklist"),
            match_value: Some(matched),
        };
    }

    if let Some(keyword) = detection_keyword {
        let reason = if attestation_required {
            "attestation+keyword"
        } else {
            "detection_keyword"
        };
        return BlockerDecision {
            blocked: true,
            reason,
            keyword: Some(keyword.clone()),
            keyword_context: None,
            match_type: Some("detection_keyword"),
            match_value: Some(keyword),
        };
    }

    BlockerDecision {
        blocked: false,
        reason: "allowed",
        keyword: None,
        keyword_context: None,
        match_type: None,
        match_value: None,
    }
}

fn evaluate_api_invocation_logic(
    config: &config::BlockerConfig,
    method_id: &str,
    annotations: &str,
) -> BlockerDecision {
    if let Some(matched) = find_keyword(&[method_id], &config.allowed_eps_active) {
        return BlockerDecision {
            blocked: false,
            reason: "allowed_whitelist",
            keyword: None,
            keyword_context: None,
            match_type: Some("allowed_whitelist"),
            match_value: Some(matched),
        };
    }

    if let Some(keyword) = find_keyword(&[method_id], &config.detection_keywords) {
        return BlockerDecision {
            blocked: true,
            reason: "detection_keyword",
            keyword: Some(keyword.clone()),
            keyword_context: Some("method"),
            match_type: Some("detection_keyword"),
            match_value: Some(keyword),
        };
    }

    if let Some(keyword) = find_keyword(&[annotations], &config.detection_keywords) {
        return BlockerDecision {
            blocked: true,
            reason: "detection_keyword",
            keyword: Some(keyword.clone()),
            keyword_context: Some("annotation"),
            match_type: Some("detection_keyword"),
            match_value: Some(keyword),
        };
    }

    BlockerDecision {
        blocked: false,
        reason: "allowed",
        keyword: None,
        keyword_context: None,
        match_type: None,
        match_value: None,
    }
}

fn write_blocker_decision(env: &mut JNIEnv, decision_obj: JObject, decision: &BlockerDecision) {
    write_decision(
        env,
        decision_obj,
        decision.blocked,
        decision.reason,
        decision.keyword.as_deref(),
        decision.keyword_context,
        decision.match_type,
        decision.match_value.as_deref(),
    );
}

fn write_decision(
    env: &mut JNIEnv,
    decision: JObject,
    blocked: bool,
    reason: &str,
    keyword: Option<&str>,
    keyword_context: Option<&str>,
    match_type: Option<&str>,
    match_value: Option<&str>,
) {
    let blocked_value = if blocked { JNI_TRUE } else { JNI_FALSE };
    if let Err(err) = env.set_field(&decision, "blocked", "Z", JValue::Bool(blocked_value)) {
        error!("failed to set blocked: {:?}", err);
    }

    set_string_field(env, &decision, "reason", Some(reason));
    set_string_field(env, &decision, "keyword", keyword);
    set_string_field(env, &decision, "keywordContext", keyword_context);
    set_string_field(env, &decision, "matchType", match_type);
    set_string_field(env, &decision, "matchValue", match_value);
}

fn set_string_field(env: &mut JNIEnv, obj: &JObject, field: &str, value: Option<&str>) {
    if let Some(text) = value {
        let jstring = match env.new_string(text) {
            Ok(value) => value,
            Err(err) => {
                error!("failed to alloc string for {}: {:?}", field, err);
                return;
            }
        };
        let j_obj = JObject::from(jstring);
        if let Err(err) = env.set_field(obj, field, "Ljava/lang/String;", JValue::Object(&j_obj)) {
            error!("failed to set string field {}: {:?}", field, err);
        }
        if let Err(err) = env.delete_local_ref(j_obj) {
            error!("failed to delete local ref for {}: {:?}", field, err);
        }
    } else {
        let null_obj = JObject::null();
        if let Err(err) = env.set_field(obj, field, "Ljava/lang/String;", JValue::Object(&null_obj)) {
            error!("failed to clear string field {}: {:?}", field, err);
        }
    }
}

#[allow(non_snake_case)]
fn evaluateEndpoint(
    mut env: JNIEnv,
    _class: JClass,
    uri: JString,
    arg0: JString,
    has_attestation: jboolean,
    decision: JObject,
) {
    if IN_LOGIN_SIGNUP.load(Ordering::Relaxed) {
        write_decision(&mut env, decision, false, "allowed_login_signup", None, None, None, None);
        return;
    }
    let uri_str: String = env.get_string(&uri).unwrap().into();
    let arg0_str: String = env.get_string(&arg0).unwrap().into();

    let config = config::get_blocker_config();
    let blocker_decision = evaluate_endpoint_logic(&config, &uri_str, &arg0_str, has_attestation == JNI_TRUE);
    write_blocker_decision(&mut env, decision, &blocker_decision);
}

#[allow(non_snake_case)]
fn evaluateNetworkRequest(
    mut env: JNIEnv,
    _class: JClass,
    url: JString,
    decision: JObject,
) {
    if IN_LOGIN_SIGNUP.load(Ordering::Relaxed) {
        write_decision(&mut env, decision, false, "allowed_login_signup", None, None, None, None);
        return;
    }
    let url_str: String = env.get_string(&url).unwrap().into();

    let config = config::get_blocker_config();
    let blocker_decision = evaluate_network_request_logic(&config, &url_str);
    write_blocker_decision(&mut env, decision, &blocker_decision);
}

#[allow(non_snake_case)]
fn shouldBlockDuplexClient(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
) -> jboolean {
    if IN_LOGIN_SIGNUP.load(Ordering::Relaxed) {
        return JNI_FALSE;
    }

    let path_str: String = env.get_string(&path).unwrap().into();

    let hermod = secstrings::get_hermod_dup();
    if path_str == hermod {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[allow(non_snake_case)]
fn evaluateAuthContext(
    mut env: JNIEnv,
    _class: JClass,
    request_path: JString,
    attestation_required: jboolean,
    decision: JObject,
) {
    if IN_LOGIN_SIGNUP.load(Ordering::Relaxed) {
        write_decision(&mut env, decision, false, "allowed_login_signup", None, None, None, None);
        return;
    }
    let request_path_str: String = env.get_string(&request_path).unwrap().into();

    let config = config::get_blocker_config();
    let blocker_decision = evaluate_auth_context_logic(&config, &request_path_str, attestation_required == JNI_TRUE);
    write_blocker_decision(&mut env, decision, &blocker_decision);
}

#[allow(non_snake_case)]
fn evaluateApiInvocation(
    mut env: JNIEnv,
    _class: JClass,
    method_id: JString,
    annotations: JString,
    decision: JObject,
) {
    if IN_LOGIN_SIGNUP.load(Ordering::Relaxed) {
        write_decision(&mut env, decision, false, "allowed_login_signup", None, None, None, None);
        return;
    }
    let method_id_str: String = env.get_string(&method_id).unwrap().into();
    let annotations_str: String = env.get_string(&annotations).unwrap().into();

    let config = config::get_blocker_config();
    let blocker_decision = evaluate_api_invocation_logic(&config, &method_id_str, &annotations_str);
    write_blocker_decision(&mut env, decision, &blocker_decision);
}

fn run_blocker_self_test(allow_unverified: bool) -> bool {
    let _ = allow_unverified;

    let config = config::get_blocker_config();
    if config.allowed_eps_active.is_empty()
        || config.detection_keywords.is_empty()
        || config.risk_block_list.is_empty()
    {
        return false;
    }

    let allowed_sample = config.allowed_eps_active[0].clone();
    let detection_sample = config.detection_keywords[0].clone();
    let risk_sample = config.risk_block_list[0].clone();

    let allowed_decision = evaluate_endpoint_logic(&config, &allowed_sample, &allowed_sample, false);
    if allowed_decision.blocked || allowed_decision.reason != "allowed_whitelist" {
        return false;
    }

    let detection_path = format!("/self_test/{}", detection_sample);
    let detection_decision = evaluate_endpoint_logic(&config, &detection_path, "", false);
    if !detection_decision.blocked {
        return false;
    }

    let attestation_decision = evaluate_endpoint_logic(&config, &detection_path, "", true);
    if !attestation_decision.blocked {
        return false;
    }

    let risk_decision = evaluate_endpoint_logic(&config, &risk_sample, "", false);
    if !risk_decision.blocked {
        return false;
    }

    let auth_detection = evaluate_auth_context_logic(&config, &detection_path, false);
    if !auth_detection.blocked {
        return false;
    }

    let auth_allowed = evaluate_auth_context_logic(&config, &allowed_sample, false);
    if auth_allowed.blocked || auth_allowed.reason != "allowed_whitelist" {
        return false;
    }

    let api_method = format!("com.snap.obf.SelfTest{}", detection_sample);
    let api_decision = evaluate_api_invocation_logic(&config, &api_method, "");
    if !api_decision.blocked || api_decision.keyword.is_none() {
        return false;
    }

    let annotation_blob = format!("@Requires{}", detection_sample);
    let api_annotation_decision = evaluate_api_invocation_logic(&config, "com.snap.obf.Safe", &annotation_blob);
    if !api_annotation_decision.blocked || api_annotation_decision.keyword_context != Some("annotation") {
        return false;
    }

    true
}

#[allow(non_snake_case)]
fn setTestMode(_env: JNIEnv, _class: JClass, test_mode: jboolean) {
    TEST_MODE.store(test_mode == JNI_TRUE, Ordering::Relaxed);
}

#[allow(non_snake_case)]
fn setInLoginSignup(_env: JNIEnv, _class: JClass, in_login_signup: jboolean) {
    IN_LOGIN_SIGNUP.store(in_login_signup == JNI_TRUE, Ordering::Relaxed);
}

#[allow(non_snake_case)]
fn runEndpointSelfTest(_env: JNIEnv, _class: JClass, test_mode: jboolean) -> jboolean {
    if run_blocker_self_test(test_mode == JNI_TRUE) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

